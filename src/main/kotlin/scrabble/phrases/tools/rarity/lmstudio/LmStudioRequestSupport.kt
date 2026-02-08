package scrabble.phrases.tools.rarity.lmstudio

import com.fasterxml.jackson.databind.ObjectMapper
import scrabble.phrases.tools.rarity.BaseWordRow
import scrabble.phrases.tools.rarity.USER_INPUT_PLACEHOLDER

enum class ResponseFormatMode {
    NONE,
    JSON_OBJECT,
    JSON_SCHEMA
}

class LmStudioRequestBuilder(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val configRegistry: LmModelConfigRegistry = LmModelConfigRegistry()
) {

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

        val estimatedTokens = (batch.size * 40) + 200
        val profileCap = config.maxTokensCap ?: Int.MAX_VALUE
        val effectiveMaxTokens = estimatedTokens
            .coerceAtLeast(256)
            .coerceAtMost(maxTokens)
            .coerceAtMost(profileCap)

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
            ResponseFormatMode.JSON_SCHEMA -> payload["response_format"] = buildJsonSchemaResponseFormat()
        }

        return mapper.writeValueAsString(payload)
    }

    private fun buildJsonSchemaResponseFormat(): Map<String, Any> {
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
            "items" to resultItemSchema
        )

        return mapOf(
            "type" to "json_schema",
            "json_schema" to mapOf(
                "name" to "rarity_batch_array",
                "schema" to responseSchema
            )
        )
    }
}

object LmStudioErrorClassifier {
    fun isUnsupportedResponseFormat(e: Exception): Boolean {
        val text = "${e.message.orEmpty()} ${e.cause?.message.orEmpty()}".lowercase()
        if (!text.contains("response_format")) return false
        return text.contains("unsupported") ||
            text.contains("unknown") ||
            text.contains("must be") ||
            text.contains("json_schema") ||
            text.contains("json object")
    }

    fun shouldSwitchToJsonSchema(e: Exception): Boolean {
        val text = "${e.message.orEmpty()} ${e.cause?.message.orEmpty()}".lowercase()
        if (!text.contains("response_format")) return false
        return text.contains("must be") && text.contains("json_schema")
    }

    fun isUnsupportedReasoningControls(e: Exception): Boolean {
        val text = "${e.message.orEmpty()} ${e.cause?.message.orEmpty()}".lowercase()
        val mentionsReasoningField = text.contains("reasoning_effort") ||
            text.contains("thinking") ||
            text.contains("chat_template_kwargs") ||
            text.contains("enable_thinking")
        if (!mentionsReasoningField) return false
        return text.contains("unsupported") ||
            text.contains("unknown") ||
            text.contains("unexpected") ||
            text.contains("invalid")
    }

    fun excerptForLog(content: String?, maxChars: Int = 500): String? {
        if (content.isNullOrBlank()) return null
        val compact = content.replace("\\s+".toRegex(), " ").trim()
        if (compact.length <= maxChars) return compact
        return compact.take(maxChars) + "...(truncated)"
    }
}
