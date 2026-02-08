package scrabble.phrases.tools.rarity

class LmModelConfigRegistry(
    private val defaultsByModelId: Map<String, LmModelConfig> = mapOf(
        MODEL_GPT_OSS_20B to GPT_OSS_20B_CONFIG,
        MODEL_GLM_47_FLASH to GLM_47_FLASH_CONFIG,
        MODEL_MINISTRAL_3_8B to MINISTRAL_3_8B_CONFIG
    ),
    private val fallback: LmModelConfig = FALLBACK_MODEL_CONFIG
) {

    fun resolve(model: String): LmModelConfig {
        val normalized = model.trim().lowercase()
        val config = defaultsByModelId[normalized] ?: fallback
        return config.copy(modelId = model)
    }
}
