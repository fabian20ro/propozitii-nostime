package scrabble.phrases.tools.rarity

import java.nio.file.Path

enum class ScoringOutputMode {
    /**
     * Step 2 mode: the model returns one structured object per word, including `rarity_level`.
     */
    SCORE_RESULTS,

    /**
     * Step 5 rebalance mode: the model returns ONLY the selected subset (most common words)
     * by batch-local ids (`local_id` in 1..N). The caller maps back to real `word_id`.
     */
    SELECTED_WORD_IDS
}

data class ScoringContext(
    val runSlug: String,
    val model: String,
    val endpoint: String,
    val maxRetries: Int,
    val timeoutSeconds: Long,
    val runLogPath: Path,
    val failedLogPath: Path,
    val systemPrompt: String,
    val userTemplate: String,
    val flavor: LmApiFlavor,
    val maxTokens: Int,
    val allowPartialResults: Boolean = false,
    val expectedJsonItems: Int? = null,
    val outputMode: ScoringOutputMode = ScoringOutputMode.SCORE_RESULTS,
    /**
     * Only meaningful for [ScoringOutputMode.SELECTED_WORD_IDS]. The parser assigns this rarity level
     * to every selected `word_id`.
     */
    val forcedRarityLevel: Int? = null
)

interface LmClient {
    fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint
    fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String)
    fun scoreBatchResilient(
        batch: List<BaseWordRow>,
        context: ScoringContext
    ): List<ScoreResult>
}
