package scrabble.phrases.tools.rarity

import java.nio.file.Path

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
    val maxTokens: Int
)

interface LmClient {
    fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint
    fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String)
    fun scoreBatchResilient(
        batch: List<BaseWordRow>,
        context: ScoringContext
    ): List<ScoreResult>
}
