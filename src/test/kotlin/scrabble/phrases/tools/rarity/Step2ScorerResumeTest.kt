package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Step2ScorerResumeTest {

    @TempDir
    lateinit var tempDir: Path

    private val testSystemPrompt: String = "test-system"
    private val testUserTemplate: String = "test-user {{INPUT_JSON}}"

    @Test
    fun restart_same_run_keeps_previous_ids_and_only_adds_new() {
        val repo = RunCsvRepository()
        val baseCsv = tempDir.resolve("step1_words.csv")
        val outputCsv = tempDir.resolve("runs/run_a.csv")

        repo.writeRows(
            baseCsv,
            BASE_CSV_HEADERS,
            listOf(
                listOf("1", "apa", "N"),
                listOf("2", "brad", "N"),
                listOf("3", "cer", "N")
            )
        )
        repo.appendRunRows(
            outputCsv,
            listOf(
                testRunRow(
                    id = 1,
                    rarityLevel = 2,
                    confidence = 0.9,
                    model = "model_a",
                    runSlug = "run_a",
                    word = "apa",
                    tag = "common"
                )
            )
        )

        val lmClient = FakeLmClient()
        val scorer = RarityStep2Scorer(
            runCsvRepository = repo,
            lmClient = lmClient,
            lockManager = RunLockManager(),
            outputDir = tempDir.resolve("build/rarity")
        )

        val options = Step2Options(
            runSlug = "run_a",
            model = "openai/gpt-oss-20b",
            baseCsvPath = baseCsv,
            outputCsvPath = outputCsv,
            inputCsvPath = null,
            batchSize = 20,
            limit = null,
            maxRetries = 2,
            timeoutSeconds = 30,
            maxTokens = 200,
            skipPreflight = true,
            force = false,
            endpointOption = null,
            baseUrlOption = null,
            systemPrompt = testSystemPrompt,
            userTemplate = testUserTemplate
        )

        scorer.execute(options)
        val afterFirst = repo.loadRunRows(outputCsv).map { it.wordId }
        val callsAfterFirst = lmClient.scoreCalls

        scorer.execute(options)
        val afterSecond = repo.loadRunRows(outputCsv).map { it.wordId }

        assertEquals(listOf(1, 2, 3), afterFirst)
        assertEquals(listOf(1, 2, 3), afterSecond)
        assertEquals(callsAfterFirst, lmClient.scoreCalls)
    }
}
