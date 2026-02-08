package scrabble.phrases.tools.rarity.lmstudio

import scrabble.phrases.tools.rarity.MODEL_GPT_OSS_20B

const val GPT_OSS_20B_TEMPERATURE: Double = 0.8
const val GPT_OSS_20B_TOP_K: Int = 40
const val GPT_OSS_20B_TOP_P: Double = 0.8
const val GPT_OSS_20B_MIN_P: Double = 0.05
const val GPT_OSS_20B_REPEAT_PENALTY: Double = 1.1
const val GPT_OSS_20B_REASONING_EFFORT: String = "low"

val GPT_OSS_20B_CONFIG: LmModelConfig = LmModelConfig(
    modelId = MODEL_GPT_OSS_20B,
    temperature = GPT_OSS_20B_TEMPERATURE,
    topK = GPT_OSS_20B_TOP_K,
    topP = GPT_OSS_20B_TOP_P,
    minP = GPT_OSS_20B_MIN_P,
    repeatPenalty = GPT_OSS_20B_REPEAT_PENALTY,
    frequencyPenalty = null,
    presencePenalty = null,
    maxTokensCap = 4096,
    reasoningEffort = GPT_OSS_20B_REASONING_EFFORT,
    enableThinking = null,
    thinkingType = null
)
