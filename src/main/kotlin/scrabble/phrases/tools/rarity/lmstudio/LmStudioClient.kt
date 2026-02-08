package scrabble.phrases.tools.rarity.lmstudio

import com.fasterxml.jackson.databind.ObjectMapper
import scrabble.phrases.tools.rarity.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import kotlin.math.ceil

private const val MAX_RECURSION_DEPTH = 10
private const val JSON_SCHEMA_UNRESOLVED_DISABLE_RATIO = 0.2

data class CapabilityState(
    val responseFormatMode: ResponseFormatMode = ResponseFormatMode.JSON_OBJECT,
    val reasoningControlsSupported: Boolean = true
)

class LmStudioClient(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val apiKey: String? = System.getenv("LMSTUDIO_API_KEY"),
    private val metrics: Step2Metrics? = null,
    private val requestBuilder: LmStudioRequestBuilder = LmStudioRequestBuilder(mapper),
    private val responseParser: LmStudioResponseParser = LmStudioResponseParser(mapper, metrics),
    private val httpGateway: LmStudioHttpGateway = LmStudioHttpGateway(apiKey)
) : LmClient {

    @Volatile
    private var capabilityState: CapabilityState = CapabilityState()

    override fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
        return httpGateway.resolveEndpoint(endpointOption, baseUrlOption)
    }

    override fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) {
        httpGateway.preflight(resolvedEndpoint, model)
    }

    override fun scoreBatchResilient(
        batch: List<BaseWordRow>,
        context: ScoringContext
    ): List<ScoreResult> {
        return scoreBatchResilientInternal(batch, context, depth = 0)
    }

    private fun scoreBatchResilientInternal(
        batch: List<BaseWordRow>,
        ctx: ScoringContext,
        depth: Int
    ): List<ScoreResult> {
        if (depth >= MAX_RECURSION_DEPTH) {
            batch.forEach { word ->
                appendJsonLine(
                    ctx.failedLogPath,
                    mapOf(
                        "ts" to OffsetDateTime.now().toString(),
                        "run" to ctx.runSlug,
                        "word_id" to word.wordId,
                        "word" to word.word,
                        "type" to word.type,
                        "error" to "max_recursion_depth_exceeded",
                        "depth" to depth
                    )
                )
            }
            return emptyList()
        }

        val direct = tryScoreBatch(batch, ctx)

        if (direct.connectivityFailure) {
            throw IllegalStateException(
                "LMStudio request failed due connectivity/timeout issues at '${ctx.endpoint}': ${direct.lastError}"
            )
        }

        if (direct.scores.isNotEmpty()) {
            if (direct.unresolved.isEmpty()) return direct.scores
            val retried = scoreBatchResilientInternal(direct.unresolved, ctx, depth + 1)
            return direct.scores + retried
        }

        if (batch.size == 1) {
            val word = batch.single()
            appendJsonLine(
                ctx.failedLogPath,
                mapOf(
                    "ts" to OffsetDateTime.now().toString(),
                    "run" to ctx.runSlug,
                    "word_id" to word.wordId,
                    "word" to word.word,
                    "type" to word.type,
                    "error" to "batch_failed_after_retries",
                    "last_error" to direct.lastError
                )
            )
            return emptyList()
        }

        val splitIndex = batch.size / 2
        val left = scoreBatchResilientInternal(batch.subList(0, splitIndex), ctx, depth + 1)
        val right = scoreBatchResilientInternal(batch.subList(splitIndex, batch.size), ctx, depth + 1)
        return left + right
    }

    private fun tryScoreBatch(
        batch: List<BaseWordRow>,
        ctx: ScoringContext
    ): BatchAttempt {
        var lastError: String? = null
        var sawOnlyConnectivityFailures = true
        val config = requestBuilder.modelConfigFor(ctx.model)
        var currentResponseFormatMode = responseFormatModeFor(ctx.flavor)
        var includeReasoningControls = shouldIncludeReasoningControls(ctx.flavor, config)

        repeat(ctx.maxRetries) { attempt ->
            var responseBody: String? = null
            val requestPayload = requestBuilder.buildRequest(
                model = ctx.model,
                batch = batch,
                systemPrompt = ctx.systemPrompt,
                userTemplate = ctx.userTemplate,
                responseFormatMode = currentResponseFormatMode,
                includeReasoningControls = includeReasoningControls,
                config = config,
                maxTokens = ctx.maxTokens
            )

            try {
                val response = httpGateway.postJsonRequest(ctx.endpoint, requestPayload, ctx.timeoutSeconds)
                responseBody = response.body
                if (response.statusCode !in 200..299) {
                    throw IllegalStateException("HTTP ${response.statusCode}: ${response.body}")
                }

                val parsed = responseParser.parse(batch, response.body)
                val disableJsonSchemaAfterPartialParse =
                    currentResponseFormatMode == ResponseFormatMode.JSON_SCHEMA &&
                        shouldDisableResponseFormatAfterPartialSchemaParse(batch.size, parsed.unresolved.size)

                if (disableJsonSchemaAfterPartialParse) {
                    markResponseFormatDisabled()
                    currentResponseFormatMode = ResponseFormatMode.NONE
                }
                appendJsonLine(
                    ctx.runLogPath,
                    mapOf(
                        "ts" to OffsetDateTime.now().toString(),
                        "run" to ctx.runSlug,
                        "attempt" to (attempt + 1),
                        "batch_size" to batch.size,
                        "parsed_count" to parsed.scores.size,
                        "unresolved_count" to parsed.unresolved.size,
                        "disable_response_format_after_partial_parse" to disableJsonSchemaAfterPartialParse,
                        "response_format_mode" to currentResponseFormatMode.name.lowercase(),
                        "reasoning_controls_enabled" to includeReasoningControls,
                        "request" to toJsonNodeOrString(requestPayload),
                        "response" to toJsonNodeOrString(response.body)
                    )
                )
                return BatchAttempt(
                    scores = parsed.scores,
                    unresolved = parsed.unresolved,
                    lastError = null,
                    connectivityFailure = false
                )
            } catch (e: Exception) {
                lastError = e.message ?: e::class.simpleName.orEmpty()
                val connectivityFailure = isConnectivityFailure(e)
                val unsupportedResponseFormat = currentResponseFormatMode != ResponseFormatMode.NONE &&
                    LmStudioErrorClassifier.isUnsupportedResponseFormat(e)
                val shouldSwitchToJsonSchema = currentResponseFormatMode == ResponseFormatMode.JSON_OBJECT &&
                    LmStudioErrorClassifier.shouldSwitchToJsonSchema(e)
                val unsupportedReasoningControls = includeReasoningControls &&
                    LmStudioErrorClassifier.isUnsupportedReasoningControls(e)
                val emptyParsedResults = currentResponseFormatMode == ResponseFormatMode.JSON_SCHEMA &&
                    LmStudioErrorClassifier.isEmptyParsedResults(e)
                val modelCrash = isModelCrash(e)

                if (!connectivityFailure) {
                    sawOnlyConnectivityFailures = false
                }
                metrics?.recordError(Step2Metrics.categorizeError(lastError))

                appendJsonLine(
                    ctx.runLogPath,
                    mapOf(
                        "ts" to OffsetDateTime.now().toString(),
                        "run" to ctx.runSlug,
                        "attempt" to (attempt + 1),
                        "batch_size" to batch.size,
                        "error" to lastError,
                        "connectivity_failure" to connectivityFailure,
                        "unsupported_response_format" to unsupportedResponseFormat,
                        "switch_to_json_schema" to shouldSwitchToJsonSchema,
                        "unsupported_reasoning_controls" to unsupportedReasoningControls,
                        "empty_parsed_results" to emptyParsedResults,
                        "response_format_mode" to currentResponseFormatMode.name.lowercase(),
                        "reasoning_controls_enabled" to includeReasoningControls,
                        "model_crash" to modelCrash,
                        "request" to requestPayload,
                        "response_excerpt" to LmStudioErrorClassifier.excerptForLog(responseBody)
                    )
                )

                if (shouldSwitchToJsonSchema) {
                    markResponseFormatJsonSchema()
                    currentResponseFormatMode = ResponseFormatMode.JSON_SCHEMA
                } else if (unsupportedResponseFormat) {
                    markResponseFormatDisabled()
                    currentResponseFormatMode = ResponseFormatMode.NONE
                } else if (emptyParsedResults) {
                    markResponseFormatDisabled()
                    currentResponseFormatMode = ResponseFormatMode.NONE
                }
                if (unsupportedReasoningControls) {
                    markReasoningControlsUnsupported()
                    includeReasoningControls = false
                }
                if (modelCrash) {
                    Thread.sleep(MODEL_CRASH_BACKOFF_MS * (attempt + 1))
                }
            }
        }

        println("Batch failed after retries (size=${batch.size}): $lastError")
        return BatchAttempt(
            scores = emptyList(),
            unresolved = batch,
            lastError = lastError,
            connectivityFailure = sawOnlyConnectivityFailures
        )
    }

    private fun shouldDisableResponseFormatAfterPartialSchemaParse(
        batchSize: Int,
        unresolvedCount: Int
    ): Boolean {
        if (batchSize <= 0 || unresolvedCount <= 0) return false
        val threshold = ceil(batchSize * JSON_SCHEMA_UNRESOLVED_DISABLE_RATIO).toInt().coerceAtLeast(1)
        return unresolvedCount >= threshold
    }

    private fun responseFormatModeFor(flavor: LmApiFlavor): ResponseFormatMode {
        if (flavor != LmApiFlavor.OPENAI_COMPAT) return ResponseFormatMode.NONE
        return capabilityState.responseFormatMode
    }

    private fun shouldIncludeReasoningControls(flavor: LmApiFlavor, config: LmModelConfig): Boolean {
        if (flavor != LmApiFlavor.OPENAI_COMPAT) return false
        if (!config.hasReasoningControls()) return false
        return capabilityState.reasoningControlsSupported
    }

    private fun updateCapability(transform: (CapabilityState) -> CapabilityState, message: String) {
        val current = capabilityState
        val updated = transform(current)
        if (updated != current) {
            synchronized(this) {
                val latest = capabilityState
                val newState = transform(latest)
                if (newState != latest) {
                    capabilityState = newState
                    println(message)
                }
            }
        }
    }

    private fun markResponseFormatJsonSchema() {
        updateCapability(
            { it.copy(responseFormatMode = ResponseFormatMode.JSON_SCHEMA) },
            "LMStudio capability: switching response_format to json_schema for this run."
        )
    }

    private fun markResponseFormatDisabled() {
        updateCapability(
            { it.copy(responseFormatMode = ResponseFormatMode.NONE) },
            "LMStudio capability: disabling response_format for this run."
        )
    }

    private fun markReasoningControlsUnsupported() {
        updateCapability(
            { it.copy(reasoningControlsSupported = false) },
            "LMStudio capability: disabling reasoning controls for this run."
        )
    }

    private fun isConnectivityFailure(e: Exception): Boolean {
        return when (e) {
            is ConnectException -> true
            is SocketTimeoutException -> true
            is HttpTimeoutException -> true
            else -> {
                val message = e.message?.lowercase().orEmpty()
                message.contains("timed out") ||
                    message.contains("connection refused") ||
                    message.contains("couldn't connect")
            }
        }
    }

    private fun isModelCrash(e: Exception): Boolean {
        val message = e.message?.lowercase().orEmpty()
        return (message.contains("model") && message.contains("crash")) ||
            message.contains("exit code")
    }

    private fun toJsonNodeOrString(value: String): Any {
        return runCatching { mapper.readTree(value) }.getOrElse { value }
    }

    private fun appendJsonLine(path: Path, payload: Any) {
        path.parent?.let { Files.createDirectories(it) }
        val line = mapper.writeValueAsString(payload) + "\n"
        Files.writeString(
            path,
            line,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}
