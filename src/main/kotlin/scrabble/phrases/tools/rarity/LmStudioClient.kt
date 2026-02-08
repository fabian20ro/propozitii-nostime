package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime

class LmStudioClient(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val apiKey: String? = System.getenv("LMSTUDIO_API_KEY"),
    private val metrics: Step2Metrics? = null,
    private val requestBuilder: LmStudioRequestBuilder = LmStudioRequestBuilder(mapper),
    private val responseParser: LmStudioResponseParser = LmStudioResponseParser(mapper, metrics),
    private val httpGateway: LmStudioHttpGateway = LmStudioHttpGateway(apiKey)
) : LmClient {

    private enum class CapabilitySupport {
        UNKNOWN,
        SUPPORTED,
        UNSUPPORTED
    }

    @Volatile
    private var responseFormatSupport: CapabilitySupport = CapabilitySupport.UNKNOWN

    @Volatile
    private var reasoningControlsSupport: CapabilitySupport = CapabilitySupport.UNKNOWN

    override fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
        return httpGateway.resolveEndpoint(endpointOption, baseUrlOption)
    }

    override fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) {
        httpGateway.preflight(resolvedEndpoint, model)
    }

    override fun scoreBatchResilient(
        batch: List<BaseWordRow>,
        runSlug: String,
        model: String,
        endpoint: String,
        maxRetries: Int,
        timeoutSeconds: Long,
        runLogPath: Path,
        failedLogPath: Path,
        systemPrompt: String,
        userTemplate: String,
        flavor: LmApiFlavor,
        maxTokens: Int
    ): List<ScoreResult> {
        val direct = tryScoreBatch(
            batch = batch,
            runSlug = runSlug,
            model = model,
            endpoint = endpoint,
            maxRetries = maxRetries,
            timeoutSeconds = timeoutSeconds,
            runLogPath = runLogPath,
            systemPrompt = systemPrompt,
            userTemplate = userTemplate,
            flavor = flavor,
            maxTokens = maxTokens
        )

        if (direct.connectivityFailure) {
            throw IllegalStateException(
                "LMStudio request failed due connectivity/timeout issues at '$endpoint': ${direct.lastError}"
            )
        }

        if (direct.scores.isNotEmpty() && direct.unresolved.isEmpty()) {
            return direct.scores
        }

        if (direct.scores.isNotEmpty() && direct.unresolved.isNotEmpty()) {
            val retried = scoreBatchResilient(
                batch = direct.unresolved,
                runSlug = runSlug,
                model = model,
                endpoint = endpoint,
                maxRetries = maxRetries,
                timeoutSeconds = timeoutSeconds,
                runLogPath = runLogPath,
                failedLogPath = failedLogPath,
                systemPrompt = systemPrompt,
                userTemplate = userTemplate,
                flavor = flavor,
                maxTokens = maxTokens
            )
            return direct.scores + retried
        }

        if (batch.size == 1) {
            appendJsonLine(
                failedLogPath,
                mapOf(
                    "ts" to OffsetDateTime.now().toString(),
                    "run" to runSlug,
                    "word_id" to batch.first().wordId,
                    "word" to batch.first().word,
                    "type" to batch.first().type,
                    "error" to "batch_failed_after_retries",
                    "last_error" to direct.lastError
                )
            )
            return emptyList()
        }

        val splitIndex = batch.size / 2
        val left = scoreBatchResilient(
            batch = batch.subList(0, splitIndex),
            runSlug = runSlug,
            model = model,
            endpoint = endpoint,
            maxRetries = maxRetries,
            timeoutSeconds = timeoutSeconds,
            runLogPath = runLogPath,
            failedLogPath = failedLogPath,
            systemPrompt = systemPrompt,
            userTemplate = userTemplate,
            flavor = flavor,
            maxTokens = maxTokens
        )
        val right = scoreBatchResilient(
            batch = batch.subList(splitIndex, batch.size),
            runSlug = runSlug,
            model = model,
            endpoint = endpoint,
            maxRetries = maxRetries,
            timeoutSeconds = timeoutSeconds,
            runLogPath = runLogPath,
            failedLogPath = failedLogPath,
            systemPrompt = systemPrompt,
            userTemplate = userTemplate,
            flavor = flavor,
            maxTokens = maxTokens
        )
        return left + right
    }

    private fun tryScoreBatch(
        batch: List<BaseWordRow>,
        runSlug: String,
        model: String,
        endpoint: String,
        maxRetries: Int,
        timeoutSeconds: Long,
        runLogPath: Path,
        systemPrompt: String,
        userTemplate: String,
        flavor: LmApiFlavor,
        maxTokens: Int
    ): BatchAttempt {
        var lastError: String? = null
        var sawOnlyConnectivityFailures = true
        val config = requestBuilder.modelConfigFor(model)
        var includeResponseFormat = shouldIncludeResponseFormat(flavor)
        var includeReasoningControls = shouldIncludeReasoningControls(flavor, config)

        repeat(maxRetries) { attempt ->
            var responseBody: String? = null
            val requestPayload = requestBuilder.buildRequest(
                model = model,
                batch = batch,
                systemPrompt = systemPrompt,
                userTemplate = userTemplate,
                includeResponseFormat = includeResponseFormat,
                includeReasoningControls = includeReasoningControls,
                config = config,
                maxTokens = maxTokens
            )

            try {
                val response = httpGateway.postJsonRequest(endpoint, requestPayload, timeoutSeconds)
                responseBody = response.body
                if (response.statusCode !in 200..299) {
                    throw IllegalStateException("HTTP ${response.statusCode}: ${response.body}")
                }

                val parsed = responseParser.parse(batch, response.body)
                appendJsonLine(
                    runLogPath,
                    mapOf(
                        "ts" to OffsetDateTime.now().toString(),
                        "run" to runSlug,
                        "attempt" to (attempt + 1),
                        "batch_size" to batch.size,
                        "parsed_count" to parsed.scores.size,
                        "unresolved_count" to parsed.unresolved.size,
                        "response_format_enabled" to includeResponseFormat,
                        "reasoning_controls_enabled" to includeReasoningControls,
                        "request" to toJsonNodeOrString(requestPayload),
                        "response" to toJsonNodeOrString(response.body)
                    )
                )
                if (includeResponseFormat) {
                    markResponseFormatSupported()
                }
                if (includeReasoningControls) {
                    markReasoningControlsSupported()
                }
                return BatchAttempt(
                    scores = parsed.scores,
                    unresolved = parsed.unresolved,
                    lastError = null,
                    connectivityFailure = false
                )
            } catch (e: Exception) {
                lastError = e.message ?: e::class.simpleName.orEmpty()
                val connectivityFailure = isConnectivityFailure(e)
                val unsupportedResponseFormat = includeResponseFormat &&
                    LmStudioErrorClassifier.isUnsupportedResponseFormat(e)
                val unsupportedReasoningControls = includeReasoningControls &&
                    LmStudioErrorClassifier.isUnsupportedReasoningControls(e)
                val modelCrash = isModelCrash(e)

                if (!connectivityFailure) {
                    sawOnlyConnectivityFailures = false
                }
                metrics?.recordError(Step2Metrics.categorizeError(lastError))

                appendJsonLine(
                    runLogPath,
                    mapOf(
                        "ts" to OffsetDateTime.now().toString(),
                        "run" to runSlug,
                        "attempt" to (attempt + 1),
                        "batch_size" to batch.size,
                        "error" to lastError,
                        "connectivity_failure" to connectivityFailure,
                        "unsupported_response_format" to unsupportedResponseFormat,
                        "unsupported_reasoning_controls" to unsupportedReasoningControls,
                        "response_format_enabled" to includeResponseFormat,
                        "reasoning_controls_enabled" to includeReasoningControls,
                        "model_crash" to modelCrash,
                        "request" to requestPayload,
                        "response_excerpt" to LmStudioErrorClassifier.excerptForLog(responseBody)
                    )
                )

                if (unsupportedResponseFormat) {
                    markResponseFormatUnsupported()
                    includeResponseFormat = false
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

    private fun shouldIncludeResponseFormat(flavor: LmApiFlavor): Boolean {
        if (flavor != LmApiFlavor.OPENAI_COMPAT) return false
        return responseFormatSupport != CapabilitySupport.UNSUPPORTED
    }

    private fun shouldIncludeReasoningControls(flavor: LmApiFlavor, config: LmModelConfig): Boolean {
        if (flavor != LmApiFlavor.OPENAI_COMPAT) return false
        if (!config.hasReasoningControls()) return false
        return reasoningControlsSupport != CapabilitySupport.UNSUPPORTED
    }

    private fun markResponseFormatSupported() {
        if (responseFormatSupport != CapabilitySupport.UNKNOWN) return
        synchronized(this) {
            if (responseFormatSupport == CapabilitySupport.UNKNOWN) {
                responseFormatSupport = CapabilitySupport.SUPPORTED
            }
        }
    }

    private fun markResponseFormatUnsupported() {
        if (responseFormatSupport == CapabilitySupport.UNSUPPORTED) return
        synchronized(this) {
            if (responseFormatSupport != CapabilitySupport.UNSUPPORTED) {
                responseFormatSupport = CapabilitySupport.UNSUPPORTED
                println("LMStudio capability: disabling response_format=json_object for this run.")
            }
        }
    }

    private fun markReasoningControlsSupported() {
        if (reasoningControlsSupport != CapabilitySupport.UNKNOWN) return
        synchronized(this) {
            if (reasoningControlsSupport == CapabilitySupport.UNKNOWN) {
                reasoningControlsSupport = CapabilitySupport.SUPPORTED
            }
        }
    }

    private fun markReasoningControlsUnsupported() {
        if (reasoningControlsSupport == CapabilitySupport.UNSUPPORTED) return
        synchronized(this) {
            if (reasoningControlsSupport != CapabilitySupport.UNSUPPORTED) {
                reasoningControlsSupport = CapabilitySupport.UNSUPPORTED
                println("LMStudio capability: disabling reasoning controls for this run.")
            }
        }
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
