package scrabble.phrases.tools.rarity.lmstudio

import scrabble.phrases.tools.rarity.MODEL_EUROLLM_22B_MLX_4BIT

const val EUROLLM_22B_TEMPERATURE: Double = 0.2
const val EUROLLM_22B_TOP_K: Int = 40
const val EUROLLM_22B_TOP_P: Double = 0.9

val EUROLLM_22B_CONFIG: LmModelConfig = LmModelConfig(
    modelId = MODEL_EUROLLM_22B_MLX_4BIT,
    temperature = EUROLLM_22B_TEMPERATURE,
    topK = EUROLLM_22B_TOP_K,
    topP = EUROLLM_22B_TOP_P,
    minP = null,
    repeatPenalty = null,
    frequencyPenalty = null,
    presencePenalty = null,
    maxTokensCap = 3072,
    reasoningEffort = null,
    enableThinking = null,
    thinkingType = null
)
