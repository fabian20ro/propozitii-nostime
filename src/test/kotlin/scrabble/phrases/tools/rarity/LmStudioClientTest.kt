package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class LmStudioClientTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun disables_response_format_for_rest_of_run_after_unsupported_error() {
        val requests = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }

            when (callIndex.getAndIncrement()) {
                0 -> respond(exchange, 400, """{"error":"'response_format.type' must be 'json_schema' or 'text'"}""")
                1 -> respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
                2 -> respond(exchange, 200, successResponseFor("brad", "N", 3, 0.8))
                else -> respond(exchange, 500, """{"error":"unexpected call"}""")
            }
        }

        try {
            val client = LmStudioClient(ObjectMapper(), apiKey = null)
            val runLogPath = tempDir.resolve("run.jsonl")
            val failedLogPath = tempDir.resolve("failed.jsonl")
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"

            val first = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                runSlug = "run_a",
                model = "model_a",
                endpoint = endpoint,
                maxRetries = 2,
                timeoutSeconds = 5,
                runLogPath = runLogPath,
                failedLogPath = failedLogPath,
                systemPrompt = SYSTEM_PROMPT,
                userTemplate = USER_PROMPT_TEMPLATE,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                maxTokens = 200
            )
            val second = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(2, "brad", "N")),
                runSlug = "run_a",
                model = "model_a",
                endpoint = endpoint,
                maxRetries = 2,
                timeoutSeconds = 5,
                runLogPath = runLogPath,
                failedLogPath = failedLogPath,
                systemPrompt = SYSTEM_PROMPT,
                userTemplate = USER_PROMPT_TEMPLATE,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                maxTokens = 200
            )

            assertEquals(1, first.size)
            assertEquals(1, second.size)
            assertEquals(3, requests.size)

            assertTrue(requests[0].contains("\"response_format\""))
            assertFalse(requests[1].contains("\"response_format\""))
            assertFalse(requests[2].contains("\"response_format\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun non_connectivity_failures_split_batches_and_log_single_word_failures() {
        val server = startServer { exchange ->
            respond(exchange, 400, """{"error":"content policy refusal"}""")
        }

        try {
            val client = LmStudioClient(ObjectMapper(), apiKey = null)
            val runLogPath = tempDir.resolve("run.jsonl")
            val failedLogPath = tempDir.resolve("failed.jsonl")
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"

            val scored = client.scoreBatchResilient(
                batch = listOf(
                    BaseWordRow(1, "cuvant1", "N"),
                    BaseWordRow(2, "cuvant2", "N")
                ),
                runSlug = "run_a",
                model = "model_a",
                endpoint = endpoint,
                maxRetries = 1,
                timeoutSeconds = 5,
                runLogPath = runLogPath,
                failedLogPath = failedLogPath,
                systemPrompt = SYSTEM_PROMPT,
                userTemplate = USER_PROMPT_TEMPLATE,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                maxTokens = 200
            )

            assertTrue(scored.isEmpty())
            val failedLines = Files.readAllLines(failedLogPath).filter { it.isNotBlank() }
            assertEquals(2, failedLines.size)
            assertTrue(failedLines.any { it.contains("\"word_id\":1") })
            assertTrue(failedLines.any { it.contains("\"word_id\":2") })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun partial_parse_retries_only_unresolved_words() {
        val requests = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }

            when (callIndex.getAndIncrement()) {
                0 -> respond(
                    exchange,
                    200,
                    successResponse(
                        """
                        [
                          {"word_id":1,"word":"cuvant1","type":"N","rarity_level":2,"tag":"common","confidence":0.8}
                        ]
                        """.trimIndent()
                    )
                )

                1 -> respond(
                    exchange,
                    200,
                    successResponse(
                        """
                        [
                          {"word_id":2,"word":"cuvant2","type":"N","rarity_level":4,"tag":"rare","confidence":0.7}
                        ]
                        """.trimIndent()
                    )
                )

                else -> respond(exchange, 500, """{"error":"unexpected call"}""")
            }
        }

        try {
            val client = LmStudioClient(ObjectMapper(), apiKey = null)
            val runLogPath = tempDir.resolve("run_partial.jsonl")
            val failedLogPath = tempDir.resolve("failed_partial.jsonl")
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"

            val scored = client.scoreBatchResilient(
                batch = listOf(
                    BaseWordRow(1, "cuvant1", "N"),
                    BaseWordRow(2, "cuvant2", "N")
                ),
                runSlug = "run_partial",
                model = "model_a",
                endpoint = endpoint,
                maxRetries = 2,
                timeoutSeconds = 5,
                runLogPath = runLogPath,
                failedLogPath = failedLogPath,
                systemPrompt = SYSTEM_PROMPT,
                userTemplate = USER_PROMPT_TEMPLATE,
                flavor = LmApiFlavor.OPENAI_COMPAT,
                maxTokens = 200
            )

            assertEquals(2, scored.size)
            assertEquals(listOf(1, 2), scored.map { it.wordId }.sorted())
            assertEquals(2, requests.size)

            val req1Content = ObjectMapper()
                .readTree(requests[0])
                .path("messages").path(1).path("content").asText("")
            val req2Content = ObjectMapper()
                .readTree(requests[1])
                .path("messages").path(1).path("content").asText("")

            assertTrue(req1Content.contains("\"word_id\":1"))
            assertTrue(req1Content.contains("\"word_id\":2"))
            assertFalse(req2Content.contains("\"word_id\":1"))
            assertTrue(req2Content.contains("\"word_id\":2"))
        } finally {
            server.stop(0)
        }
    }

    private fun startServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange -> handler(exchange) }
        server.start()
        return server
    }

    private fun respond(exchange: HttpExchange, statusCode: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { out -> out.write(bytes) }
    }

    private fun successResponseFor(word: String, type: String, rarity: Int, confidence: Double): String {
        return successResponse(
            """[{"word":"$word","type":"$type","rarity_level":$rarity,"tag":"common","confidence":$confidence}]"""
        )
    }

    private fun successResponse(resultsArrayJson: String): String {
        val escaped = resultsArrayJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")
        return """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"results\":$escaped}"
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
