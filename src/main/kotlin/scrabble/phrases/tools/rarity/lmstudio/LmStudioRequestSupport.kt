package scrabble.phrases.tools.rarity.lmstudio

import com.fasterxml.jackson.databind.ObjectMapper
import scrabble.phrases.tools.rarity.BaseWordRow
import scrabble.phrases.tools.rarity.USER_INPUT_PLACEHOLDER

enum class ResponseFormatMode {
    NONE,
    JSON_OBJECT,
    JSON_SCHEMA
}

enum class JsonSchemaKind {
    SCORE_RESULTS,
    SELECTED_WORD_IDS
}

class LmStudioRequestBuilder(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val configRegistry: LmModelConfigRegistry = LmModelConfigRegistry()
) {
    companion object {
        private const val SCORE_MIN_MAX_TOKENS = 256
        private const val SCORE_TOKENS_PER_ITEM = 40
        private const val SCORE_BASE_TOKENS = 200

        private const val SELECTION_MIN_MAX_TOKENS = 128
        private const val SELECTION_TOKENS_PER_ITEM = 24
        private const val SELECTION_BASE_TOKENS = 128
        private const val SELECTION_HARD_MAX_TOKENS = 1024
    }

    fun modelConfigFor(model: String): LmModelConfig {
        return configRegistry.resolve(model)
    }

    fun buildRequest(
        model: String,
        batch: List<BaseWordRow>,
        systemPrompt: String,
        userTemplate: String,
        responseFormatMode: ResponseFormatMode,
        includeReasoningControls: Boolean,
        config: LmModelConfig,
        maxTokens: Int,
        expectedItems: Int? = null,
        schemaKind: JsonSchemaKind = JsonSchemaKind.SCORE_RESULTS
    ): String {
        val entriesJson = mapper.writeValueAsString(
            when (schemaKind) {
                JsonSchemaKind.SCORE_RESULTS -> batch.map {
                    mapOf(
                        "word_id" to it.wordId,
                        "word" to it.word,
                        "type" to it.type
                    )
                }
                JsonSchemaKind.SELECTED_WORD_IDS -> batch.mapIndexed { index, row ->
                    mapOf(
                        "local_id" to (index + 1),
                        "word" to row.word
                    )
                }
            }
        )

        val userPrompt = if (userTemplate.contains(USER_INPUT_PLACEHOLDER)) {
            userTemplate.replace(USER_INPUT_PLACEHOLDER, entriesJson)
        } else {
            "$userTemplate\n\nIntrări:\n$entriesJson"
        }

        val estimatedTokens = when (schemaKind) {
            JsonSchemaKind.SCORE_RESULTS -> (batch.size * SCORE_TOKENS_PER_ITEM) + SCORE_BASE_TOKENS
            JsonSchemaKind.SELECTED_WORD_IDS -> {
                val expected = (expectedItems ?: 0).coerceAtLeast(1)
                (expected * SELECTION_TOKENS_PER_ITEM) + SELECTION_BASE_TOKENS
            }
        }
        val profileCap = config.maxTokensCap ?: Int.MAX_VALUE
        val effectiveMaxTokens = when (schemaKind) {
            JsonSchemaKind.SCORE_RESULTS -> estimatedTokens
                .coerceAtLeast(SCORE_MIN_MAX_TOKENS)
                .coerceAtMost(maxTokens)
                .coerceAtMost(profileCap)
            JsonSchemaKind.SELECTED_WORD_IDS -> estimatedTokens
                .coerceAtLeast(SELECTION_MIN_MAX_TOKENS)
                .coerceAtMost(maxTokens)
                .coerceAtMost(profileCap)
                .coerceAtMost(SELECTION_HARD_MAX_TOKENS)
        }

        val payload = linkedMapOf<String, Any>(
            "model" to model,
            "temperature" to config.temperature,
            "max_tokens" to effectiveMaxTokens,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )

        config.topK?.let { payload["top_k"] = it }
        config.topP?.let { payload["top_p"] = it }
        config.minP?.let { payload["min_p"] = it }
        config.repeatPenalty?.let { payload["repeat_penalty"] = it }
        config.frequencyPenalty?.let { payload["frequency_penalty"] = it }
        config.presencePenalty?.let { payload["presence_penalty"] = it }

        if (includeReasoningControls) {
            config.reasoningEffort?.let { payload["reasoning_effort"] = it }
            config.thinkingType?.let { payload["thinking"] = mapOf("type" to it) }
            config.enableThinking?.let { payload["chat_template_kwargs"] = mapOf("enable_thinking" to it) }
        }

        when (responseFormatMode) {
            ResponseFormatMode.NONE -> Unit
            ResponseFormatMode.JSON_OBJECT -> payload["response_format"] = mapOf("type" to "json_object")
            ResponseFormatMode.JSON_SCHEMA -> {
                val effectiveExpected = expectedItems ?: batch.size
                payload["response_format"] = when (schemaKind) {
                    JsonSchemaKind.SCORE_RESULTS -> buildScoreResultsJsonSchemaResponseFormat(effectiveExpected)
                    JsonSchemaKind.SELECTED_WORD_IDS -> buildSelectedWordIdsJsonSchemaResponseFormat(
                        expectedItems = effectiveExpected,
                        maxLocalId = batch.size
                    )
                }
            }
        }

        return mapper.writeValueAsString(payload)
    }

    private fun buildScoreResultsJsonSchemaResponseFormat(expectedItems: Int): Map<String, Any> {
        val boundedExpectedItems = expectedItems.coerceAtLeast(1)
        val resultItemSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "word_id" to mapOf("type" to "integer"),
                "word" to mapOf("type" to "string"),
                "type" to mapOf("type" to "string"),
                "rarity_level" to mapOf(
                    "type" to "integer",
                    "minimum" to 1,
                    "maximum" to 5
                ),
                "tag" to mapOf("type" to "string"),
                "confidence" to mapOf(
                    "type" to "number",
                    "minimum" to 0,
                    "maximum" to 1
                )
            ),
            "required" to listOf("word_id", "word", "type", "rarity_level", "tag", "confidence"),
            "additionalProperties" to false
        )

        val responseSchema = mapOf(
            "type" to "array",
            "items" to resultItemSchema,
            "minItems" to boundedExpectedItems,
            "maxItems" to boundedExpectedItems
        )

        return mapOf(
            "type" to "json_schema",
            "json_schema" to mapOf(
                "name" to "rarity_batch_array",
                "schema" to responseSchema
            )
        )
    }

    private fun buildSelectedWordIdsJsonSchemaResponseFormat(
        expectedItems: Int,
        maxLocalId: Int
    ): Map<String, Any> {
        val boundedExpectedItems = expectedItems.coerceAtLeast(1)
        val boundedMaxLocalId = maxLocalId.coerceAtLeast(1)
        val responseSchema = mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "integer",
                "minimum" to 1,
                "maximum" to boundedMaxLocalId
            ),
            "minItems" to boundedExpectedItems,
            "maxItems" to boundedExpectedItems,
            "uniqueItems" to true
        )

        return mapOf(
            "type" to "json_schema",
            "json_schema" to mapOf(
                "name" to "selected_word_ids",
                "schema" to responseSchema
            )
        )
    }
}

object LmStudioErrorClassifier {

    private fun errorTextOf(e: Exception): String =
        "${e.message.orEmpty()} ${e.cause?.message.orEmpty()}".lowercase()

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it) }

    fun isUnsupportedResponseFormat(e: Exception): Boolean {
        val text = errorTextOf(e)
        if (!text.contains("response_format")) return false
        return text.containsAny("unsupported", "unknown", "must be", "json_schema", "json object")
    }

    fun shouldSwitchToJsonSchema(e: Exception): Boolean {
        val text = errorTextOf(e)
        return text.contains("response_format") &&
            text.contains("must be") &&
            text.contains("json_schema")
    }

    fun isUnsupportedReasoningControls(e: Exception): Boolean {
        val text = errorTextOf(e)
        val mentionsReasoningField = text.containsAny(
            "reasoning_effort", "thinking", "chat_template_kwargs", "enable_thinking"
        )
        if (!mentionsReasoningField) return false
        return text.containsAny("unsupported", "unknown", "unexpected", "invalid")
    }

    fun isEmptyParsedResults(e: Exception): Boolean =
        errorTextOf(e).contains("no valid results parsed from 0 result nodes")

    fun excerptForLog(content: String?, maxChars: Int = 500): String? {
        if (content.isNullOrBlank()) return null
        val compact = content.replace("\\s+".toRegex(), " ").trim()
        if (compact.length <= maxChars) return compact
        return compact.take(maxChars) + "...(truncated)"
    }
}
