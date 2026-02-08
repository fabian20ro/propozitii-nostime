package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper

data class ModelRequestProfile(
    val temperature: Double,
    val topP: Double?,
    val disableThinking: Boolean,
    val maxTokensCap: Int?
)

class LmStudioRequestBuilder(
    private val mapper: ObjectMapper = ObjectMapper()
) {

    fun requestProfileFor(model: String): ModelRequestProfile {
        return when (model.trim().lowercase()) {
            MODEL_GPT_OSS_20B -> ModelRequestProfile(
                temperature = 0.0,
                topP = 0.9,
                disableThinking = false,
                maxTokensCap = null
            )

            MODEL_GLM_47_FLASH -> ModelRequestProfile(
                temperature = 0.0,
                topP = 0.2,
                disableThinking = true,
                maxTokensCap = 2048
            )

            MODEL_MINISTRAL_3_8B -> ModelRequestProfile(
                temperature = 0.0,
                topP = 0.4,
                disableThinking = false,
                maxTokensCap = 3072
            )

            else -> ModelRequestProfile(
                temperature = 0.0,
                topP = 0.5,
                disableThinking = false,
                maxTokensCap = null
            )
        }
    }

    fun buildRequest(
        model: String,
        batch: List<BaseWordRow>,
        systemPrompt: String,
        userTemplate: String,
        includeResponseFormat: Boolean,
        includeReasoningControls: Boolean,
        profile: ModelRequestProfile,
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
        val profileCap = profile.maxTokensCap ?: Int.MAX_VALUE
        val effectiveMaxTokens = estimatedTokens
            .coerceAtLeast(256)
            .coerceAtMost(maxTokens)
            .coerceAtMost(profileCap)

        val payload = linkedMapOf<String, Any>(
            "model" to model,
            "temperature" to profile.temperature,
            "max_tokens" to effectiveMaxTokens,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            )
        )

        profile.topP?.let { payload["top_p"] = it }
        if (includeReasoningControls && profile.disableThinking) {
            payload["reasoning_effort"] = "low"
            payload["chat_template_kwargs"] = mapOf("enable_thinking" to false)
        }
        if (includeResponseFormat) {
            payload["response_format"] = mapOf("type" to "json_object")
        }

        return mapper.writeValueAsString(payload)
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

    fun isUnsupportedReasoningControls(e: Exception): Boolean {
        val text = "${e.message.orEmpty()} ${e.cause?.message.orEmpty()}".lowercase()
        val mentionsReasoningField = text.contains("reasoning_effort") ||
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
