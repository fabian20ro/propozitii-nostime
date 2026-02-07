package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import java.time.OffsetDateTime

interface LmClient {
    fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint
    fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String)
    fun scoreBatchResilient(
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
    ): List<ScoreResult>
}

class LmStudioClient(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val apiKey: String? = System.getenv("LMSTUDIO_API_KEY"),
    private val metrics: Step2Metrics? = null
) : LmClient {
    private enum class ResponseFormatSupport {
        UNKNOWN,
        SUPPORTED,
        UNSUPPORTED
    }

    @Volatile
    private var responseFormatSupport: ResponseFormatSupport = ResponseFormatSupport.UNKNOWN

    override fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
        if (!endpointOption.isNullOrBlank()) {
            val normalized = endpointOption.trim()
            val uri = URI.create(normalized)
            val path = uri.path.orEmpty()

            if (path.isBlank() || path == "/") {
                return detectFromBase(normalized, "explicit-base")
            }
            resolveExplicitEndpoint(normalized, path)?.let { return it }

            return ResolvedEndpoint(
                endpoint = normalized,
                modelsEndpoint = null,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                source = "explicit-endpoint-unknown-path"
            )
        }

        val baseUrl = (baseUrlOption ?: DEFAULT_LMSTUDIO_BASE_URL).trim().trimEnd('/')
        return detectFromBase(baseUrl, "auto")
    }

    private fun resolveExplicitEndpoint(endpoint: String, path: String): ResolvedEndpoint? {
        return when {
            path.contains("/api/v1/chat") -> ResolvedEndpoint(
                endpoint = endpoint,
                modelsEndpoint = endpoint.substringBefore("/api/v1/chat") + LMSTUDIO_MODELS_PATH,
                flavor = LmApiFlavor.LMSTUDIO_REST,
                source = "explicit-endpoint"
            )

            path.contains("/v1/chat/completions") -> ResolvedEndpoint(
                endpoint = endpoint,
                modelsEndpoint = endpoint.substringBefore("/v1/chat/completions") + OPENAI_MODELS_PATH,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                source = "explicit-endpoint"
            )

            else -> null
        }
    }

    override fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) {
        val modelsEndpoint = resolvedEndpoint.modelsEndpoint ?: return

        val response = getRequest(modelsEndpoint, DEFAULT_PREFLIGHT_TIMEOUT_SECONDS)
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("LMStudio preflight failed: HTTP ${response.statusCode} from $modelsEndpoint")
        }

        if (!response.body.contains(model)) {
            println("Warning: model '$model' was not found in $modelsEndpoint response.")
        }
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
        if (direct.scores != null) return direct.scores
        if (direct.connectivityFailure) {
            throw IllegalStateException(
                "LMStudio request failed due connectivity/timeout issues at '$endpoint': ${direct.lastError}"
            )
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
        var includeResponseFormat = shouldIncludeResponseFormat(flavor)

        repeat(maxRetries) { attempt ->
            val requestPayload = buildLmRequest(
                model = model,
                batch = batch,
                systemPrompt = systemPrompt,
                userTemplate = userTemplate,
                includeResponseFormat = includeResponseFormat,
                maxTokens = maxTokens
            )
            try {
                val response = postJsonRequest(endpoint, requestPayload, timeoutSeconds)
                if (response.statusCode !in 200..299) {
                    throw IllegalStateException("HTTP ${response.statusCode}: ${response.body}")
                }

                val parsed = parseLmResponse(batch, response.body)
                appendJsonLine(
                    runLogPath,
                    mapOf(
                        "ts" to OffsetDateTime.now().toString(),
                        "run" to runSlug,
                        "attempt" to (attempt + 1),
                        "batch_size" to batch.size,
                        "response_format_enabled" to includeResponseFormat,
                        "request" to mapper.readTree(requestPayload),
                        "response" to mapper.readTree(response.body)
                    )
                )
                if (includeResponseFormat) {
                    markResponseFormatSupported()
                }
                return BatchAttempt(scores = parsed, lastError = null, connectivityFailure = false)
            } catch (e: Exception) {
                lastError = e.message ?: e::class.simpleName.orEmpty()
                val connectivityFailure = isConnectivityFailure(e)
                val unsupportedResponseFormat = includeResponseFormat && isUnsupportedResponseFormat(e)
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
                        "response_format_enabled" to includeResponseFormat,
                        "model_crash" to modelCrash,
                        "request" to requestPayload
                    )
                )
                if (unsupportedResponseFormat) {
                    markResponseFormatUnsupported()
                    includeResponseFormat = false
                }
                if (modelCrash) {
                    Thread.sleep(MODEL_CRASH_BACKOFF_MS * (attempt + 1))
                }
            }
        }

        println("Batch failed after retries (size=${batch.size}): $lastError")
        return BatchAttempt(scores = null, lastError = lastError, connectivityFailure = sawOnlyConnectivityFailures)
    }

    private fun buildLmRequest(
        model: String,
        batch: List<BaseWordRow>,
        systemPrompt: String,
        userTemplate: String,
        includeResponseFormat: Boolean,
        maxTokens: Int
    ): String {
        val entriesJson = mapper.writeValueAsString(batch.map { mapOf("word" to it.word, "type" to it.type) })
        val userPrompt = if (userTemplate.contains(USER_INPUT_PLACEHOLDER)) {
            userTemplate.replace(USER_INPUT_PLACEHOLDER, entriesJson)
        } else {
            "$userTemplate\n\nIntrări:\n$entriesJson"
        }

        val estimatedTokens = (batch.size * 120) + 50
        val effectiveMaxTokens = maxOf(estimatedTokens, maxTokens)

        val payload = linkedMapOf<String, Any>(
            "model" to model,
            "temperature" to 0,
            "max_tokens" to effectiveMaxTokens,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )
        if (includeResponseFormat) {
            payload["response_format"] = mapOf("type" to "json_object")
        }

        return mapper.writeValueAsString(payload)
    }

    private fun parseLmResponse(batch: List<BaseWordRow>, responseBody: String): List<ScoreResult> {
        val root = mapper.readTree(responseBody)
        val content = extractModelContent(root)
            ?: throw IllegalStateException("LMStudio response missing assistant content")

        val repairedContent = JsonRepair.repair(content)
        if (repairedContent != content) {
            metrics?.recordJsonRepair()
        }

        val contentJson = mapper.readTree(repairedContent)
        val results = contentJson.path("results")
        if (!results.isArray) {
            throw IllegalStateException("LMStudio content is missing 'results' array")
        }

        return parseResultsLenient(batch, results)
    }

    private fun parseResultsLenient(
        batch: List<BaseWordRow>,
        results: JsonNode
    ): List<ScoreResult> {
        val scored = mutableListOf<ScoreResult>()
        val resultCount = results.size().coerceAtMost(batch.size)

        for (i in 0 until resultCount) {
            val parsed = parseScoreNodeLenient(batch[i], results[i])
            if (parsed != null) {
                scored += parsed
            }
        }

        if (scored.isEmpty() && batch.isNotEmpty()) {
            throw IllegalStateException(
                "No valid results parsed from ${results.size()} result nodes for batch of ${batch.size}"
            )
        }

        return scored
    }

    private fun parseScoreNodeLenient(expected: BaseWordRow, node: JsonNode): ScoreResult? {
        return try {
            val word = node.path("word").asText("")
            val type = node.path("type").asText("")
            val rarity = node.path("rarity_level").asInt(-1)
            val tag = node.path("tag").asText("uncertain")
            val rawConfidence = parseConfidence(node.path("confidence"))

            val wordMatches = word == expected.word ||
                FuzzyWordMatcher.matches(expected.word, word)
            val typeMatches = type == expected.type ||
                type.isBlank() // accept missing type if word matches

            if (!wordMatches || (!typeMatches && type.isNotBlank())) {
                return null
            }

            if (word != expected.word) {
                metrics?.recordFuzzyMatch()
            }

            if (rarity !in 1..5) return null

            val confidence = rawConfidence.coerceIn(0.0, 1.0)
            if (confidence.isNaN()) return null

            ScoreResult(
                wordId = expected.wordId,
                word = expected.word,
                type = expected.type,
                rarityLevel = rarity,
                tag = tag.take(16),
                confidence = confidence
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseConfidence(node: JsonNode): Double {
        if (node.isNumber) return node.asDouble(Double.NaN)
        if (node.isTextual) {
            return node.asText("").toDoubleOrNull() ?: Double.NaN
        }
        return Double.NaN
    }

    private fun detectFromBase(baseUrl: String, source: String): ResolvedEndpoint {
        val openAiModelsUrl = "$baseUrl$OPENAI_MODELS_PATH"
        if (probeEndpoint(openAiModelsUrl)) {
            return ResolvedEndpoint(
                endpoint = "$baseUrl$OPENAI_CHAT_COMPLETIONS_PATH",
                modelsEndpoint = openAiModelsUrl,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                source = "$source-openai"
            )
        }

        val lmStudioModelsUrl = "$baseUrl$LMSTUDIO_MODELS_PATH"
        if (probeEndpoint(lmStudioModelsUrl)) {
            return ResolvedEndpoint(
                endpoint = "$baseUrl$LMSTUDIO_CHAT_PATH",
                modelsEndpoint = lmStudioModelsUrl,
                flavor = LmApiFlavor.LMSTUDIO_REST,
                source = "$source-lmstudio"
            )
        }

        return ResolvedEndpoint(
            endpoint = "$baseUrl$OPENAI_CHAT_COMPLETIONS_PATH",
            modelsEndpoint = openAiModelsUrl,
            flavor = LmApiFlavor.OPENAI_COMPAT,
            source = "$source-fallback"
        )
    }

    private fun probeEndpoint(url: String): Boolean {
        return try {
            val response = getRequest(url, DEFAULT_PREFLIGHT_TIMEOUT_SECONDS)
            response.statusCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun extractModelContent(root: JsonNode): String? {
        val primary = nodeToContentText(root.path("choices").path(0).path("message").path("content"))
        if (!primary.isNullOrBlank()) return primary

        val fallbackMessage = nodeToContentText(root.path("message").path("content"))
        if (!fallbackMessage.isNullOrBlank()) return fallbackMessage

        val fallbackOutputText = nodeToContentText(root.path("output_text"))
        if (!fallbackOutputText.isNullOrBlank()) return fallbackOutputText

        return null
    }

    private fun nodeToContentText(node: JsonNode?): String? {
        if (node == null || node.isMissingNode || node.isNull) return null

        return when {
            node.isTextual -> stripCodeFences(node.asText())
            node.isArray -> {
                val joined = node.mapNotNull { part ->
                    if (part.isTextual) part.asText()
                    else if (part.isObject) part.path("text").asText(null)
                    else null
                }.joinToString("")
                stripCodeFences(joined).ifBlank { null }
            }

            node.isObject -> stripCodeFences(mapper.writeValueAsString(node))
            else -> stripCodeFences(node.toString())
        }
    }

    private fun stripCodeFences(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun isUnsupportedResponseFormat(e: Exception): Boolean {
        val text = buildString {
            append(e.message?.lowercase().orEmpty())
            append(" ")
            append(e.cause?.message?.lowercase().orEmpty())
        }
        if (!text.contains("response_format")) return false
        return text.contains("unsupported") ||
            text.contains("unknown") ||
            text.contains("must be") ||
            text.contains("json_schema") ||
            text.contains("json object")
    }

    private fun shouldIncludeResponseFormat(flavor: LmApiFlavor): Boolean {
        if (flavor != LmApiFlavor.OPENAI_COMPAT) return false
        return responseFormatSupport != ResponseFormatSupport.UNSUPPORTED
    }

    private fun markResponseFormatSupported() {
        if (responseFormatSupport != ResponseFormatSupport.UNKNOWN) return
        synchronized(this) {
            if (responseFormatSupport == ResponseFormatSupport.UNKNOWN) {
                responseFormatSupport = ResponseFormatSupport.SUPPORTED
            }
        }
    }

    private fun markResponseFormatUnsupported() {
        if (responseFormatSupport == ResponseFormatSupport.UNSUPPORTED) return
        synchronized(this) {
            if (responseFormatSupport != ResponseFormatSupport.UNSUPPORTED) {
                responseFormatSupport = ResponseFormatSupport.UNSUPPORTED
                println("LMStudio capability: disabling response_format=json_object for this run.")
            }
        }
    }

    private fun isConnectivityFailure(e: Exception): Boolean {
        return when (e) {
            is java.net.ConnectException -> true
            is java.net.SocketTimeoutException -> true
            is java.net.http.HttpTimeoutException -> true
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

    private fun appendJsonLine(path: Path, payload: Any) {
        path.parent?.let { java.nio.file.Files.createDirectories(it) }
        val line = mapper.writeValueAsString(payload) + "\n"
        java.nio.file.Files.writeString(
            path,
            line,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )
    }

    private data class HttpResponsePayload(
        val statusCode: Int,
        val body: String
    )

    private fun postJsonRequest(
        url: String,
        requestBody: String,
        timeoutSeconds: Long
    ): HttpResponsePayload {
        val timeoutMillis = toTimeoutMillis(timeoutSeconds)
        val connection = (URI.create(url).toURL().openConnection(Proxy.NO_PROXY) as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            applyJsonHeaders(contentType = true)
        }

        return try {
            connection.outputStream.use { out ->
                out.write(requestBody.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val status = connection.responseCode
            val body = readConnectionBody(connection)
            HttpResponsePayload(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun getRequest(
        url: String,
        timeoutSeconds: Long
    ): HttpResponsePayload {
        val timeoutMillis = toTimeoutMillis(timeoutSeconds)
        val connection = (URI.create(url).toURL().openConnection(Proxy.NO_PROXY) as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            applyJsonHeaders(contentType = false)
        }

        return try {
            val status = connection.responseCode
            val body = readConnectionBody(connection)
            HttpResponsePayload(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readConnectionBody(connection: java.net.HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..399) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun toTimeoutMillis(timeoutSeconds: Long): Int {
        return (timeoutSeconds * 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun java.net.HttpURLConnection.applyJsonHeaders(contentType: Boolean) {
        if (contentType) {
            setRequestProperty("Content-Type", "application/json")
        }
        setRequestProperty("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
    }
}
