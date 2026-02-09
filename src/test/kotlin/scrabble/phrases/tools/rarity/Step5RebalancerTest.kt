package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Step5RebalancerTest {

    @TempDir
    lateinit var tempDir: Path

    private val repo = RunCsvRepository()

    @Test
    fun parseTransitions_defaults_and_custom() {
        val defaults = parseStep5Transitions(null)
        assertEquals(listOf(LevelTransition(2, 1), LevelTransition(3, 2), LevelTransition(4, 3)), defaults)

        val custom = parseStep5Transitions("4:3,2:1")
        assertEquals(listOf(LevelTransition(2, 1), LevelTransition(4, 3)), custom)
    }

    @Test
    fun parseTransitions_rejects_invalid_and_duplicate_from_level() {
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("5:5")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("2:1,2:2")
        }
    }

    @Test
    fun rebalance_uses_step2_csv_and_downgrades_exact_target_count() {
        val inputCsv = tempDir.resolve("step2_run.csv")
        val outputCsv = tempDir.resolve("step5_rebalanced.csv")

        repo.appendRunRows(
            inputCsv,
            listOf(
                testRunRow(id = 21, rarityLevel = 2, word = "a21"),
                testRunRow(id = 22, rarityLevel = 2, word = "a22"),
                testRunRow(id = 23, rarityLevel = 2, word = "a23"),
                testRunRow(id = 24, rarityLevel = 2, word = "a24"),
                testRunRow(id = 25, rarityLevel = 2, word = "a25"),
                testRunRow(id = 26, rarityLevel = 2, word = "a26"),
                testRunRow(id = 31, rarityLevel = 3, word = "b31"),
                testRunRow(id = 32, rarityLevel = 3, word = "b32")
            )
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 5, // never proposes the target lower level
                tag = "uncertain",
                confidence = 0.5
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_test",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 6,
                lowerRatio = 1.0 / 3.0,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 42L,
                transitions = listOf(LevelTransition(fromLevel = 2, toLevel = 1)),
                systemPrompt = REBALANCE_SYSTEM_PROMPT,
                userTemplate = REBALANCE_USER_PROMPT_TEMPLATE
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val rowsById = rows.associateBy { it["word_id"]!!.toInt() }

        val level1Ids = rowsById.values
            .filter { it["final_level"] == "1" }
            .map { it["word_id"]!!.toInt() }
            .sorted()
        assertEquals(listOf(21, 22), level1Ids)

        assertEquals("2", rowsById[23]!!["final_level"])
        assertEquals("3", rowsById[31]!!["final_level"])
        assertTrue(rowsById[21]!!["rebalance_rule"]!!.startsWith("2->1"))
        assertEquals("step5_test", rowsById[21]!!["rebalance_run"])
        assertTrue(rowsById[31]!!["rebalance_rule"].isNullOrBlank())
    }

    @Test
    fun rebalance_processes_each_word_at_most_once_per_run() {
        val inputCsv = tempDir.resolve("step2_run_once.csv")
        val outputCsv = tempDir.resolve("step5_once.csv")

        repo.appendRunRows(
            inputCsv,
            listOf(
                testRunRow(id = 1, rarityLevel = 2, word = "w1"),
                testRunRow(id = 2, rarityLevel = 2, word = "w2"),
                testRunRow(id = 3, rarityLevel = 2, word = "w3"),
                testRunRow(id = 4, rarityLevel = 2, word = "w4"),
                testRunRow(id = 5, rarityLevel = 2, word = "w5"),
                testRunRow(id = 6, rarityLevel = 2, word = "w6")
            )
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 2,
                tag = "uncertain",
                confidence = 0.4
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_once",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 6,
                lowerRatio = 1.0 / 3.0,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 7L,
                transitions = listOf(
                    LevelTransition(fromLevel = 2, toLevel = 2), // picks 1/3 at level 2, promotes rest to 3
                    LevelTransition(fromLevel = 3, toLevel = 2) // should not re-process promoted words
                ),
                systemPrompt = REBALANCE_SYSTEM_PROMPT,
                userTemplate = REBALANCE_USER_PROMPT_TEMPLATE
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val byFinalLevel = rows.groupBy { it["final_level"] }
        assertEquals(2, byFinalLevel["2"]?.size ?: 0)
        assertEquals(4, byFinalLevel["3"]?.size ?: 0)
        assertEquals(1, lm.scoreCalls) // second transition gets zero eligible rows
    }

    @Test
    fun rebalance_rounds_target_count_for_one_third_batches() {
        val inputCsv = tempDir.resolve("step2_run_60.csv")
        val outputCsv = tempDir.resolve("step5_run_60.csv")

        repo.appendRunRows(
            inputCsv,
            (1..60).map { id -> testRunRow(id = id, rarityLevel = 2, word = "w$id") }
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 2,
                tag = "uncertain",
                confidence = 0.5
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_60",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 60,
                lowerRatio = 0.3333,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 1L,
                transitions = listOf(LevelTransition(fromLevel = 2, toLevel = 1)),
                systemPrompt = REBALANCE_SYSTEM_PROMPT,
                userTemplate = REBALANCE_USER_PROMPT_TEMPLATE
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level1Count = rows.count { it["final_level"] == "1" }
        val level2Count = rows.count { it["final_level"] == "2" }
        assertEquals(20, level1Count)
        assertEquals(40, level2Count)
    }

    @Test
    fun rebalance_supports_equal_split_for_keep_promote_mode() {
        val inputCsv = tempDir.resolve("step2_run_60_level4.csv")
        val outputCsv = tempDir.resolve("step5_run_60_level4_split.csv")

        repo.appendRunRows(
            inputCsv,
            (1..60).map { id -> testRunRow(id = id, rarityLevel = 4, word = "w$id") }
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 4,
                tag = "uncertain",
                confidence = 0.5
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_60_equal",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 60,
                lowerRatio = 0.5,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 2L,
                transitions = listOf(LevelTransition(fromLevel = 4, toLevel = 4)),
                systemPrompt = REBALANCE_SYSTEM_PROMPT,
                userTemplate = REBALANCE_USER_PROMPT_TEMPLATE
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level4Count = rows.count { it["final_level"] == "4" }
        val level5Count = rows.count { it["final_level"] == "5" }
        assertEquals(30, level4Count)
        assertEquals(30, level5Count)
    }
}
