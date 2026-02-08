package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import scrabble.phrases.tools.rarity.lmstudio.GLM_47_FLASH_TEMPERATURE
import scrabble.phrases.tools.rarity.lmstudio.GLM_47_FLASH_TOP_K
import scrabble.phrases.tools.rarity.lmstudio.GLM_47_FLASH_TOP_P
import scrabble.phrases.tools.rarity.lmstudio.EUROLLM_22B_TEMPERATURE
import scrabble.phrases.tools.rarity.lmstudio.EUROLLM_22B_TOP_K
import scrabble.phrases.tools.rarity.lmstudio.EUROLLM_22B_TOP_P
import scrabble.phrases.tools.rarity.lmstudio.GPT_OSS_20B_MIN_P
import scrabble.phrases.tools.rarity.lmstudio.GPT_OSS_20B_REASONING_EFFORT
import scrabble.phrases.tools.rarity.lmstudio.GPT_OSS_20B_REPEAT_PENALTY
import scrabble.phrases.tools.rarity.lmstudio.GPT_OSS_20B_TEMPERATURE
import scrabble.phrases.tools.rarity.lmstudio.GPT_OSS_20B_TOP_K
import scrabble.phrases.tools.rarity.lmstudio.GPT_OSS_20B_TOP_P
import scrabble.phrases.tools.rarity.lmstudio.LmStudioClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class LmStudioClientTest {

    @TempDir
    lateinit var tempDir: Path

    private val mapper = ObjectMapper()

    private fun ctx(
        runSlug: String = "run_test",
        model: String = "model_a",
        endpoint: String = "http://localhost",
        maxRetries: Int = 1,
        timeoutSeconds: Long = 5,
        runLogPath: Path = tempDir.resolve("run.jsonl"),
        failedLogPath: Path = tempDir.resolve("failed.jsonl"),
        maxTokens: Int = 8000
    ): ScoringContext {
        return ScoringContext(
            runSlug = runSlug,
            model = model,
            endpoint = endpoint,
            maxRetries = maxRetries,
            timeoutSeconds = timeoutSeconds,
            runLogPath = runLogPath,
            failedLogPath = failedLogPath,
            systemPrompt = SYSTEM_PROMPT,
            userTemplate = USER_PROMPT_TEMPLATE,
            flavor = LmApiFlavor.OPENAI_COMPAT,
            maxTokens = maxTokens
        )
    }

    @Test
    fun switches_response_format_to_json_schema_after_json_object_is_unsupported() {
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
            val client = LmStudioClient(mapper, apiKey = null)
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"
            val context = ctx(runSlug = "run_a", endpoint = endpoint, maxRetries = 2, maxTokens = 200)

            val first = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = context
            )
            val second = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(2, "brad", "N")),
                context = context
            )

            assertEquals(1, first.size)
            assertEquals(1, second.size)
            assertEquals(3, requests.size)

            assertTrue(requests[0].contains("\"response_format\""))
            assertTrue(requests[0].contains("\"type\":\"json_object\""))
            assertTrue(requests[1].contains("\"response_format\""))
            assertTrue(requests[1].contains("\"type\":\"json_schema\""))
            val schema = mapper.readTree(requests[1])
                .path("response_format")
                .path("json_schema")
                .path("schema")
            assertEquals(1, schema.path("minItems").asInt())
            assertEquals(1, schema.path("maxItems").asInt())
            assertTrue(requests[2].contains("\"response_format\""))
            assertTrue(requests[2].contains("\"type\":\"json_schema\""))
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
            val client = LmStudioClient(mapper, apiKey = null)
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"
            val failedLogPath = tempDir.resolve("failed.jsonl")

            val scored = client.scoreBatchResilient(
                batch = listOf(
                    BaseWordRow(1, "cuvant1", "N"),
                    BaseWordRow(2, "cuvant2", "N")
                ),
                context = ctx(runSlug = "run_a", endpoint = endpoint, failedLogPath = failedLogPath, maxTokens = 200)
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
            val client = LmStudioClient(mapper, apiKey = null)
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"

            val scored = client.scoreBatchResilient(
                batch = listOf(
                    BaseWordRow(1, "cuvant1", "N"),
                    BaseWordRow(2, "cuvant2", "N")
                ),
                context = ctx(
                    runSlug = "run_partial",
                    endpoint = endpoint,
                    maxRetries = 2,
                    runLogPath = tempDir.resolve("run_partial.jsonl"),
                    failedLogPath = tempDir.resolve("failed_partial.jsonl"),
                    maxTokens = 200
                )
            )

            assertEquals(2, scored.size)
            assertEquals(listOf(1, 2), scored.map { it.wordId }.sorted())
            assertEquals(2, requests.size)

            val req1Content = mapper.readTree(requests[0])
                .path("messages").path(1).path("content").asText("")
            val req2Content = mapper.readTree(requests[1])
                .path("messages").path(1).path("content").asText("")

            assertTrue(req1Content.contains("\"word_id\":1"))
            assertTrue(req1Content.contains("\"word_id\":2"))
            assertFalse(req2Content.contains("\"word_id\":1"))
            assertTrue(req2Content.contains("\"word_id\":2"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun parses_top_level_array_content() {
        val server = startServer { exchange ->
            respond(
                exchange,
                200,
                successResponseRawContent(
                    """[{"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":0.9}]"""
                )
            )
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_array",
                    model = MODEL_MINISTRAL_3_8B,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_array.jsonl"),
                    failedLogPath = tempDir.resolve("failed_array.jsonl")
                )
            )

            assertEquals(1, scored.size)
            assertEquals(2, scored.first().rarityLevel)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun parses_items_array_content() {
        val server = startServer { exchange ->
            respond(
                exchange,
                200,
                successResponseRawContent(
                    """
                    {
                      "items": [
                        {"word_id":1,"word":"apa","type":"N","rarity_level":3,"tag":"less_common","confidence":0.7}
                      ]
                    }
                    """.trimIndent()
                )
            )
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_items",
                    model = MODEL_MINISTRAL_3_8B,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_items.jsonl"),
                    failedLogPath = tempDir.resolve("failed_items.jsonl")
                )
            )

            assertEquals(1, scored.size)
            assertEquals(3, scored.first().rarityLevel)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun extracts_json_object_when_content_has_reasoning_text() {
        val server = startServer { exchange ->
            respond(
                exchange,
                200,
                successResponseRawContent(
                    """
                    Thinking...
                    {"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":4,"tag":"rare","confidence":0.6}]}
                    Done.
                    """.trimIndent()
                )
            )
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_reasoning_text",
                    model = MODEL_GLM_47_FLASH,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_reasoning_text.jsonl"),
                    failedLogPath = tempDir.resolve("failed_reasoning_text.jsonl")
                )
            )

            assertEquals(1, scored.size)
            assertEquals(4, scored.first().rarityLevel)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun extracts_json_after_numbered_reasoning_prefix() {
        val server = startServer { exchange ->
            respond(
                exchange,
                200,
                successResponseRawContent(
                    """
                    1. Analyze request
                    2. Build response
                    {"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":0.8}]}
                    """.trimIndent()
                )
            )
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_numbered_reasoning_text",
                    model = MODEL_GLM_47_FLASH,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_numbered_reasoning_text.jsonl"),
                    failedLogPath = tempDir.resolve("failed_numbered_reasoning_text.jsonl")
                )
            )

            assertEquals(1, scored.size)
            assertEquals(2, scored.first().rarityLevel)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun salvages_valid_results_when_one_item_in_results_array_is_malformed() {
        val requests = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }

            when (callIndex.getAndIncrement()) {
                0 -> respond(
                    exchange,
                    200,
                    successResponseRawContent(
                        """
                        {
                          "results": [
                            {"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":0.9},
                            {"word_id":2,"word":"brad","type":"N","rarity_level":4,"tag":"rare","confidence" 0.8}
                          ]
                        }
                        """.trimIndent()
                    )
                )
                1 -> respond(
                    exchange,
                    200,
                    successResponseRawContent(
                        """
                        {
                          "results": [
                            {"word_id":2,"word":"brad","type":"N","rarity_level":4,"tag":"rare","confidence":0.8}
                          ]
                        }
                        """.trimIndent()
                    )
                )
                else -> respond(exchange, 500, """{"error":"unexpected call"}""")
            }
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(
                    BaseWordRow(1, "apa", "N"),
                    BaseWordRow(2, "brad", "N")
                ),
                context = ctx(
                    runSlug = "run_salvage_malformed_item",
                    model = MODEL_MINISTRAL_3_8B,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_salvage_malformed_item.jsonl"),
                    failedLogPath = tempDir.resolve("failed_salvage_malformed_item.jsonl")
                )
            )

            assertEquals(2, scored.size)
            assertEquals(listOf(1, 2), scored.map { it.wordId }.sorted())
            assertEquals(2, requests.size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun disables_response_format_if_json_schema_is_also_unsupported() {
        val requests = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }

            when (callIndex.getAndIncrement()) {
                0 -> respond(exchange, 400, """{"error":"'response_format.type' must be 'json_schema' or 'text'"}""")
                1 -> respond(exchange, 400, """{"error":"response_format json_schema unsupported"}""")
                2 -> respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
                else -> respond(exchange, 500, """{"error":"unexpected call"}""")
            }
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_schema_unsupported",
                    model = MODEL_GLM_47_FLASH,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    maxRetries = 3,
                    runLogPath = tempDir.resolve("run_schema_unsupported.jsonl"),
                    failedLogPath = tempDir.resolve("failed_schema_unsupported.jsonl")
                )
            )

            assertEquals(1, scored.size)
            assertEquals(3, requests.size)
            assertTrue(requests[0].contains("\"type\":\"json_object\""))
            assertTrue(requests[1].contains("\"type\":\"json_schema\""))
            assertFalse(requests[2].contains("\"response_format\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun disables_response_format_after_empty_json_schema_results() {
        val requests = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }

            when (callIndex.getAndIncrement()) {
                0 -> respond(exchange, 400, """{"error":"'response_format.type' must be 'json_schema' or 'text'"}""")
                1 -> respond(exchange, 200, successResponseRawContent("[]"))
                2 -> respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
                else -> respond(exchange, 500, """{"error":"unexpected call"}""")
            }
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val scored = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_empty_schema_results",
                    model = MODEL_EUROLLM_22B,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    maxRetries = 3,
                    runLogPath = tempDir.resolve("run_empty_schema_results.jsonl"),
                    failedLogPath = tempDir.resolve("failed_empty_schema_results.jsonl")
                )
            )

            assertEquals(1, scored.size)
            assertEquals(3, requests.size)
            assertTrue(requests[0].contains("\"type\":\"json_object\""))
            assertTrue(requests[1].contains("\"type\":\"json_schema\""))
            assertFalse(requests[2].contains("\"response_format\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun disables_reasoning_controls_for_rest_of_run_after_unsupported_error_for_glm() {
        val requests = mutableListOf<String>()
        val callIndex = AtomicInteger(0)
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }

            when (callIndex.getAndIncrement()) {
                0 -> respond(exchange, 400, """{"error":"unknown field 'reasoning_effort'"}""")
                1 -> respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
                2 -> respond(exchange, 200, successResponseFor("brad", "N", 3, 0.8))
                else -> respond(exchange, 500, """{"error":"unexpected call"}""")
            }
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            val endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions"
            val context = ctx(
                runSlug = "run_glm_reasoning",
                model = MODEL_GLM_47_FLASH,
                endpoint = endpoint,
                maxRetries = 2,
                runLogPath = tempDir.resolve("run_glm_reasoning.jsonl"),
                failedLogPath = tempDir.resolve("failed_glm_reasoning.jsonl")
            )

            val first = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = context
            )
            val second = client.scoreBatchResilient(
                batch = listOf(BaseWordRow(2, "brad", "N")),
                context = context
            )

            assertEquals(1, first.size)
            assertEquals(1, second.size)
            assertEquals(3, requests.size)
            assertTrue(requests[0].contains("\"reasoning_effort\":\"low\""))
            assertTrue(requests[0].contains("\"chat_template_kwargs\""))
            assertFalse(requests[1].contains("\"reasoning_effort\""))
            assertFalse(requests[2].contains("\"reasoning_effort\""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun glm_profile_uses_dynamic_max_tokens_and_sampling_defaults() {
        val requests = mutableListOf<String>()
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }
            respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_glm_profile",
                    model = MODEL_GLM_47_FLASH,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_glm_profile.jsonl"),
                    failedLogPath = tempDir.resolve("failed_glm_profile.jsonl")
                )
            )

            val request = mapper.readTree(requests.single())
            assertEquals(256, request.path("max_tokens").asInt())
            assertEquals(GLM_47_FLASH_TEMPERATURE, request.path("temperature").asDouble())
            assertEquals(GLM_47_FLASH_TOP_P, request.path("top_p").asDouble())
            assertEquals(GLM_47_FLASH_TOP_K, request.path("top_k").asInt())
            assertEquals("low", request.path("reasoning_effort").asText())
            assertEquals("disabled", request.path("thinking").path("type").asText())
            assertFalse(request.path("chat_template_kwargs").path("enable_thinking").asBoolean(true))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun gpt_oss_profile_includes_sampling_and_reasoning_defaults() {
        val requests = mutableListOf<String>()
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }
            respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_gpt_profile",
                    model = MODEL_GPT_OSS_20B,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_gpt_profile.jsonl"),
                    failedLogPath = tempDir.resolve("failed_gpt_profile.jsonl")
                )
            )

            val request = mapper.readTree(requests.single())
            assertEquals(GPT_OSS_20B_TEMPERATURE, request.path("temperature").asDouble())
            assertEquals(GPT_OSS_20B_TOP_K, request.path("top_k").asInt())
            assertEquals(GPT_OSS_20B_TOP_P, request.path("top_p").asDouble())
            assertEquals(GPT_OSS_20B_MIN_P, request.path("min_p").asDouble())
            assertEquals(GPT_OSS_20B_REPEAT_PENALTY, request.path("repeat_penalty").asDouble())
            assertEquals(GPT_OSS_20B_REASONING_EFFORT, request.path("reasoning_effort").asText())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun eurollm_profile_uses_simple_sampling_and_no_reasoning_controls() {
        val requests = mutableListOf<String>()
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }
            respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_eurollm_profile",
                    model = MODEL_EUROLLM_22B_MLX_4BIT,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_eurollm_profile.jsonl"),
                    failedLogPath = tempDir.resolve("failed_eurollm_profile.jsonl")
                )
            )

            val request = mapper.readTree(requests.single())
            assertEquals(EUROLLM_22B_TEMPERATURE, request.path("temperature").asDouble())
            assertEquals(EUROLLM_22B_TOP_K, request.path("top_k").asInt())
            assertEquals(EUROLLM_22B_TOP_P, request.path("top_p").asDouble())
            assertFalse(request.has("reasoning_effort"))
            assertFalse(request.has("thinking"))
            assertFalse(request.has("chat_template_kwargs"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun eurollm_short_model_id_uses_eurollm_profile_defaults() {
        val requests = mutableListOf<String>()
        val server = startServer { exchange ->
            val requestBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            synchronized(requests) { requests += requestBody }
            respond(exchange, 200, successResponseFor("apa", "N", 2, 0.9))
        }

        try {
            val client = LmStudioClient(mapper, apiKey = null)
            client.scoreBatchResilient(
                batch = listOf(BaseWordRow(1, "apa", "N")),
                context = ctx(
                    runSlug = "run_eurollm_short_id_profile",
                    model = MODEL_EUROLLM_22B,
                    endpoint = "http://127.0.0.1:${server.address.port}/v1/chat/completions",
                    runLogPath = tempDir.resolve("run_eurollm_short_id_profile.jsonl"),
                    failedLogPath = tempDir.resolve("failed_eurollm_short_id_profile.jsonl")
                )
            )

            val request = mapper.readTree(requests.single())
            assertEquals(EUROLLM_22B_TEMPERATURE, request.path("temperature").asDouble())
            assertEquals(EUROLLM_22B_TOP_K, request.path("top_k").asInt())
            assertEquals(EUROLLM_22B_TOP_P, request.path("top_p").asDouble())
            assertFalse(request.has("reasoning_effort"))
            assertFalse(request.has("thinking"))
            assertFalse(request.has("chat_template_kwargs"))
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
        val resultsJson = """[{"word":"$word","type":"$type","rarity_level":$rarity,"tag":"common","confidence":$confidence}]"""
        val escaped = escapeJsonContent("""{"results":$resultsJson}""")
            .replace("\n", "")
        return wrapInChatResponse(escaped)
    }

    private fun successResponse(resultsArrayJson: String): String {
        val escaped = escapeJsonContent("""{"results":$resultsArrayJson}""")
            .replace("\n", "")
        return wrapInChatResponse(escaped)
    }

    private fun successResponseRawContent(content: String): String {
        val escaped = escapeJsonContent(content)
            .replace("\n", "\\n")
        return wrapInChatResponse(escaped)
    }

    private fun escapeJsonContent(content: String): String {
        return content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
    }

    private fun wrapInChatResponse(escapedContent: String): String {
        return """
            {
              "choices": [
                {
                  "message": {
                    "content": "$escapedContent"
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
