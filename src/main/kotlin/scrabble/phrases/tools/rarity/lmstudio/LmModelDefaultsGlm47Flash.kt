package scrabble.phrases.tools.rarity.lmstudio

import scrabble.phrases.tools.rarity.MODEL_GLM_47_FLASH

const val GLM_47_FLASH_TEMPERATURE: Double = 0.7
const val GLM_47_FLASH_TOP_K: Int = 50
const val GLM_47_FLASH_TOP_P: Double = 0.95
const val GLM_47_FLASH_REASONING_EFFORT: String = "low"

val GLM_47_FLASH_CONFIG: LmModelConfig = LmModelConfig(
    modelId = MODEL_GLM_47_FLASH,
    temperature = GLM_47_FLASH_TEMPERATURE,
    topK = GLM_47_FLASH_TOP_K,
    topP = GLM_47_FLASH_TOP_P,
    minP = null,
    repeatPenalty = null,
    frequencyPenalty = null,
    presencePenalty = null,
    maxTokensCap = 2048,
    reasoningEffort = GLM_47_FLASH_REASONING_EFFORT,
    enableThinking = false,
    thinkingType = "disabled"
)
