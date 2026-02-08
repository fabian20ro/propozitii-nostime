package scrabble.phrases.tools.rarity.lmstudio

import scrabble.phrases.tools.rarity.MODEL_MINISTRAL_3_8B

const val MINISTRAL_3_8B_TEMPERATURE: Double = 0.3
const val MINISTRAL_3_8B_TOP_K: Int = 40
const val MINISTRAL_3_8B_TOP_P: Double = 0.9

val MINISTRAL_3_8B_CONFIG: LmModelConfig = LmModelConfig(
    modelId = MODEL_MINISTRAL_3_8B,
    temperature = MINISTRAL_3_8B_TEMPERATURE,
    topK = MINISTRAL_3_8B_TOP_K,
    topP = MINISTRAL_3_8B_TOP_P,
    minP = null,
    repeatPenalty = null,
    frequencyPenalty = null,
    presencePenalty = null,
    maxTokensCap = 3072,
    reasoningEffort = null,
    enableThinking = null,
    thinkingType = null
)
