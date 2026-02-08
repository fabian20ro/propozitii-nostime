package scrabble.phrases.tools.rarity.lmstudio

import scrabble.phrases.tools.rarity.MODEL_GLM_47_FLASH
import scrabble.phrases.tools.rarity.MODEL_GPT_OSS_20B
import scrabble.phrases.tools.rarity.MODEL_MINISTRAL_3_8B
import scrabble.phrases.tools.rarity.MODEL_EUROLLM_22B_MLX_4BIT

class LmModelConfigRegistry(
    defaultsByModelId: Map<String, LmModelConfig> = mapOf(
        MODEL_GPT_OSS_20B to GPT_OSS_20B_CONFIG,
        MODEL_GLM_47_FLASH to GLM_47_FLASH_CONFIG,
        MODEL_MINISTRAL_3_8B to MINISTRAL_3_8B_CONFIG,
        MODEL_EUROLLM_22B_MLX_4BIT to EUROLLM_22B_CONFIG
    ),
    private val fallback: LmModelConfig = FALLBACK_MODEL_CONFIG
) {
    private val defaultsByModelId: Map<String, LmModelConfig> =
        defaultsByModelId.mapKeys { (key, _) -> key.lowercase() }

    fun resolve(model: String): LmModelConfig {
        val normalized = model.trim().lowercase()
        val config = defaultsByModelId[normalized] ?: fallback
        return config.copy(modelId = model)
    }
}
