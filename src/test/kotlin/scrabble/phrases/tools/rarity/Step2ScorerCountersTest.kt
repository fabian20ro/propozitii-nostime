package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Step2ScorerCountersTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun counters_track_mixed_scored_and_failed_rows() {
        val repo = RunCsvRepository()
        val baseCsv = tempDir.resolve("step1_words.csv")
        val outputCsv = tempDir.resolve("runs/run_mix.csv")
        val outputDir = tempDir.resolve("build/rarity")

        repo.writeRows(
            baseCsv,
            BASE_CSV_HEADERS,
            listOf(
                listOf("1", "apa", "N"),
                listOf("2", "brad", "N"),
                listOf("3", "cer", "N"),
                listOf("4", "deal", "N")
            )
        )

        val scorer = RarityStep2Scorer(
            runCsvRepository = repo,
            lmClient = HalfBatchLmClient(),
            lockManager = RunLockManager(),
            outputDir = outputDir
        )

        val options = Step2Options(
            runSlug = "run_mix",
            model = "model_x",
            baseCsvPath = baseCsv,
            outputCsvPath = outputCsv,
            inputCsvPath = null,
            batchSize = 2,
            limit = null,
            maxRetries = 1,
            timeoutSeconds = 5,
            maxTokens = 128,
            skipPreflight = true,
            force = false,
            endpointOption = null,
            baseUrlOption = null,
            systemPrompt = SYSTEM_PROMPT,
            userTemplate = USER_PROMPT_TEMPLATE
        )

        scorer.execute(options)

        val rows = repo.loadRunRows(outputCsv)
        assertEquals(listOf(1, 3), rows.map { it.wordId })

        val statePath = outputDir.resolve("runs/run_mix.state.json")
        assertTrue(statePath.toFile().exists())
        val state = ObjectMapper().readTree(statePath.toFile())
        assertEquals("completed", state.path("status").asText())
        assertEquals(2, state.path("scored").asInt())
        assertEquals(2, state.path("failed").asInt())
        assertEquals(4, state.path("pending").asInt())
    }
}

private class HalfBatchLmClient : LmClient {
    override fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
        return ResolvedEndpoint("http://localhost", null, LmApiFlavor.OPENAI_COMPAT, "test")
    }

    override fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) = Unit

    override fun scoreBatchResilient(
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
    ): List<ScoreResult> {
        val first = batch.firstOrNull() ?: return emptyList()
        return listOf(
            ScoreResult(
                wordId = first.wordId,
                word = first.word,
                type = first.type,
                rarityLevel = 2,
                tag = "common",
                confidence = 0.8
            )
        )
    }
}
