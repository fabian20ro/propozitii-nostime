package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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

    private data class ParsedBatch(
        val scores: List<ScoreResult>,
        val unresolved: List<BaseWordRow>
    )

    private data class ScoreCandidate(
        val wordId: Int?,
        val word: String?,
        val type: String?,
        val rarityLevel: Int?,
        val tag: String,
        val confidence: Double
    )

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
            return direct.scores
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
                        "parsed_count" to parsed.scores.size,
                        "unresolved_count" to parsed.unresolved.size,
                        "response_format_enabled" to includeResponseFormat,
                        "request" to mapper.readTree(requestPayload),
                        "response" to mapper.readTree(response.body)
                    )
                )
                if (includeResponseFormat) {
                    markResponseFormatSupported()
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
        return BatchAttempt(
            scores = emptyList(),
            unresolved = batch,
            lastError = lastError,
            connectivityFailure = sawOnlyConnectivityFailures
        )
    }

    private fun buildLmRequest(
        model: String,
        batch: List<BaseWordRow>,
        systemPrompt: String,
        userTemplate: String,
        includeResponseFormat: Boolean,
        maxTokens: Int
    ): String {
        val entriesJson = mapper.writeValueAsString(
            batch.map {
                mapOf(
                    "word_id" to it.wordId,
                    "word" to it.word,
                    "type" to it.type
                )
            }
        )
        val userPrompt = if (userTemplate.contains(USER_INPUT_PLACEHOLDER)) {
            userTemplate.replace(USER_INPUT_PLACEHOLDER, entriesJson)
        } else {
            "$userTemplate\n\nIntrări:\n$entriesJson"
        }

        val estimatedTokens = (batch.size * 26) + 120
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

    private fun parseLmResponse(batch: List<BaseWordRow>, responseBody: String): ParsedBatch {
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
    ): ParsedBatch {
        if (batch.isEmpty()) {
            return ParsedBatch(scores = emptyList(), unresolved = emptyList())
        }

        val pendingByWordId = batch.associateBy { it.wordId }.toMutableMap()
        val pendingByWordType = batch
            .groupBy { it.word to it.type }
            .mapValues { (_, rows) -> rows.toMutableList() }
            .toMutableMap()

        val scored = mutableListOf<ScoreResult>()
        for (i in 0 until results.size()) {
            val candidate = parseScoreCandidate(results[i]) ?: continue
            val matched = matchCandidate(candidate, pendingByWordId, pendingByWordType) ?: continue

            scored += ScoreResult(
                wordId = matched.wordId,
                word = matched.word,
                type = matched.type,
                rarityLevel = candidate.rarityLevel ?: continue,
                tag = candidate.tag.ifBlank { "uncertain" }.take(16),
                confidence = candidate.confidence
            )
        }

        val unresolved = pendingByWordId.values.sortedBy { it.wordId }

        if (scored.isEmpty() && unresolved.size == batch.size) {
            throw IllegalStateException(
                "No valid results parsed from ${results.size()} result nodes for batch of ${batch.size}"
            )
        }

        if (unresolved.isNotEmpty()) {
            metrics?.recordError(Step2Metrics.ErrorCategory.WORD_MISMATCH)
        }

        return ParsedBatch(scores = scored, unresolved = unresolved)
    }

    private fun parseScoreCandidate(node: JsonNode): ScoreCandidate? {
        val rarity = node.path("rarity_level").asInt(-1)
        if (rarity !in 1..5) return null

        val wordId = when {
            node.path("word_id").isInt -> node.path("word_id").asInt()
            node.path("word_id").isTextual -> node.path("word_id").asText("").toIntOrNull()
            else -> null
        }

        val word = node.path("word").asText("").ifBlank { null }
        val type = node.path("type").asText("").ifBlank { null }
        val tag = node.path("tag").asText("uncertain")
        val confidence = normalizeConfidence(parseConfidence(node.path("confidence")))

        return ScoreCandidate(
            wordId = wordId,
            word = word,
            type = type,
            rarityLevel = rarity,
            tag = tag,
            confidence = confidence
        )
    }

    private fun matchCandidate(
        candidate: ScoreCandidate,
        pendingByWordId: MutableMap<Int, BaseWordRow>,
        pendingByWordType: MutableMap<Pair<String, String>, MutableList<BaseWordRow>>
    ): BaseWordRow? {
        candidate.wordId?.let { id ->
            val row = pendingByWordId.remove(id) ?: return@let
            val key = row.word to row.type
            pendingByWordType.removeById(key, id)
            return row
        }

        val word = candidate.word ?: return null
        val type = candidate.type ?: return null
        val exactKey = word to type

        val exact = pendingByWordType.removeFirstOrNull(exactKey)
        if (exact != null) {
            pendingByWordId.remove(exact.wordId)
            return exact
        }

        val fuzzy = pendingByWordType.removeFirstFuzzy(word, type)
        if (fuzzy != null) {
            pendingByWordId.remove(fuzzy.wordId)
            metrics?.recordFuzzyMatch()
            return fuzzy
        }

        return null
    }

    private fun parseConfidence(node: JsonNode): Double {
        return when {
            node.isNumber -> node.asDouble(Double.NaN)
            node.isTextual -> node.asText("").toDoubleOrNull() ?: Double.NaN
            else -> Double.NaN
        }
    }

    private fun normalizeConfidence(value: Double): Double {
        if (value.isNaN()) return 0.5
        if (value in 0.0..1.0) return value
        if (value > 1.0 && value <= 100.0) return value / 100.0
        return 0.5
    }

    private fun MutableMap<Pair<String, String>, MutableList<BaseWordRow>>.removeById(
        key: Pair<String, String>,
        wordId: Int
    ) {
        val rows = this[key] ?: return
        rows.removeIf { it.wordId == wordId }
        if (rows.isEmpty()) {
            remove(key)
        }
    }

    private fun MutableMap<Pair<String, String>, MutableList<BaseWordRow>>.removeFirstOrNull(
        key: Pair<String, String>
    ): BaseWordRow? {
        val rows = this[key] ?: return null
        if (rows.isEmpty()) {
            remove(key)
            return null
        }
        val row = rows.removeAt(0)
        if (rows.isEmpty()) {
            remove(key)
        }
        return row
    }

    private fun MutableMap<Pair<String, String>, MutableList<BaseWordRow>>.removeFirstFuzzy(
        word: String,
        type: String
    ): BaseWordRow? {
        val match = entries.firstOrNull { (key, rows) ->
            key.second == type && rows.isNotEmpty() && FuzzyWordMatcher.matches(key.first, word)
        } ?: return null

        val rows = match.value
        val row = rows.removeAt(0)
        if (rows.isEmpty()) {
            remove(match.key)
        }
        return row
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
        return nodeToContentText(root.path("choices").path(0).path("message").path("content"))
            ?: nodeToContentText(root.path("message").path("content"))
            ?: nodeToContentText(root.path("output_text"))
    }

    private fun nodeToContentText(node: JsonNode?): String? {
        if (node == null || node.isMissingNode || node.isNull) return null

        val raw = when {
            node.isTextual -> node.asText()
            node.isArray -> node.mapNotNull { part ->
                when {
                    part.isTextual -> part.asText()
                    part.isObject -> part.path("text").asText(null)
                    else -> null
                }
            }.joinToString("")
            node.isObject -> mapper.writeValueAsString(node)
            else -> node.toString()
        }

        return stripCodeFences(raw).ifBlank { null }
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
        val text = "${e.message.orEmpty()} ${e.cause?.message.orEmpty()}".lowercase()
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
        val connection = (URI.create(url).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
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
        val connection = (URI.create(url).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
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

    private fun readConnectionBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..399) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun toTimeoutMillis(timeoutSeconds: Long): Int {
        return (timeoutSeconds * 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun HttpURLConnection.applyJsonHeaders(contentType: Boolean) {
        if (contentType) {
            setRequestProperty("Content-Type", "application/json")
        }
        setRequestProperty("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) {
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
    }
}
