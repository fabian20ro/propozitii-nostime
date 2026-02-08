package scrabble.phrases.tools.rarity.lmstudio

data class LmModelConfig(
    val modelId: String,
    val temperature: Double,
    val topK: Int?,
    val topP: Double?,
    val minP: Double?,
    val repeatPenalty: Double?,
    val frequencyPenalty: Double?,
    val presencePenalty: Double?,
    val maxTokensCap: Int?,
    val reasoningEffort: String?,
    val enableThinking: Boolean?,
    val thinkingType: String?
) {
    fun hasReasoningControls(): Boolean {
        return reasoningEffort != null || enableThinking != null || thinkingType != null
    }
}
