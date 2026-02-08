package scrabble.phrases.tools.rarity.lmstudio

import scrabble.phrases.tools.rarity.*
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI

class LmStudioHttpGateway(
    private val apiKey: String?
) {

    fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
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

    fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) {
        val modelsEndpoint = resolvedEndpoint.modelsEndpoint ?: return

        val response = getRequest(modelsEndpoint, DEFAULT_PREFLIGHT_TIMEOUT_SECONDS)
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("LMStudio preflight failed: HTTP ${response.statusCode} from $modelsEndpoint")
        }

        if (!response.body.contains(model)) {
            println("Warning: model '$model' was not found in $modelsEndpoint response.")
        }
    }

    fun postJsonRequest(
        url: String,
        requestBody: String,
        timeoutSeconds: Long
    ): LmHttpResponse {
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
            LmHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
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

    private fun probeEndpoint(url: String): Boolean {
        return try {
            val response = getRequest(url, DEFAULT_PREFLIGHT_TIMEOUT_SECONDS)
            response.statusCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun getRequest(url: String, timeoutSeconds: Long): LmHttpResponse {
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
            LmHttpResponse(status, body)
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

data class LmHttpResponse(
    val statusCode: Int,
    val body: String
)
