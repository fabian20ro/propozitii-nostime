package scrabble.phrases.tools.rarity

import java.nio.file.Path

interface LmClient {
    fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint
    fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String)
    fun scoreBatchResilient(
        batch: List<BaseWordRow>,
        runSlug: String,
        model: String,
        endpoint: String,
        maxRetries: Int,
        timeoutSeconds: Long,
        runLogPath: Path,
        failedLogPath: Path,
        systemPrompt: String,
        userTemplate: String,
        flavor: LmApiFlavor,
        maxTokens: Int
    ): List<ScoreResult>
}
