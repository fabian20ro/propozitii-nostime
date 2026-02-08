package scrabble.phrases.tools.rarity.lmstudio

const val FALLBACK_MODEL_TEMPERATURE: Double = 0.0
const val FALLBACK_MODEL_TOP_K: Int = 40
const val FALLBACK_MODEL_TOP_P: Double = 1.0

val FALLBACK_MODEL_CONFIG: LmModelConfig = LmModelConfig(
    modelId = "fallback",
    temperature = FALLBACK_MODEL_TEMPERATURE,
    topK = FALLBACK_MODEL_TOP_K,
    topP = FALLBACK_MODEL_TOP_P,
    minP = null,
    repeatPenalty = null,
    frequencyPenalty = null,
    presencePenalty = null,
    maxTokensCap = null,
    reasoningEffort = null,
    enableThinking = null,
    thinkingType = null
)
