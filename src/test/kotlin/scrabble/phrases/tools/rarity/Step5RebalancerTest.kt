package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Step5RebalancerTest {

    @TempDir
    lateinit var tempDir: Path

    private val repo = RunCsvRepository()
    private val testSystemPrompt: String = "test-system"
    private val testUserTemplate: String = "test-user {{INPUT_JSON}}"

    @Test
    fun parseTransitions_defaults_and_custom() {
        val defaults = parseStep5Transitions(null)
        assertEquals(listOf(LevelTransition(2, 1), LevelTransition(3, 2), LevelTransition(4, 3)), defaults)

        val custom = parseStep5Transitions("4:3,2:1")
        assertEquals(listOf(LevelTransition(2, 1), LevelTransition(4, 3)), custom)

        val pair = parseStep5Transitions("2-3:2")
        assertEquals(listOf(LevelTransition(fromLevel = 2, toLevel = 2, fromLevelUpper = 3)), pair)
    }

    @Test
    fun parseTransitions_rejects_invalid_and_overlapping_sources() {
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("5:5")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("2:1,2:2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("2-4:2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("2-3:1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseStep5Transitions("2-3:2,3:2")
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
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
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

        val switchedLog = tempDir.resolve("build/rarity/rebalance/switched_words/step5_test.switched.jsonl")
        assertTrue(Files.exists(switchedLog))
        val switchedLines = Files.readAllLines(switchedLog)
        assertEquals(2, switchedLines.size)
        assertTrue(switchedLines.all { it.contains("\"previous_level\":2") && it.contains("\"new_level\":1") })
        assertTrue(switchedLines.any { it.contains("\"word_id\":21") })
        assertTrue(switchedLines.any { it.contains("\"word_id\":22") })
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
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
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
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
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
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level4Count = rows.count { it["final_level"] == "4" }
        val level5Count = rows.count { it["final_level"] == "5" }
        assertEquals(30, level4Count)
        assertEquals(30, level5Count)
    }

    @Test
    fun rebalance_pair_transition_supports_arbitrary_ratio_split() {
        val inputCsv = tempDir.resolve("step2_run_60_levels_2_3.csv")
        val outputCsv = tempDir.resolve("step5_run_60_levels_2_3.csv")

        repo.appendRunRows(
            inputCsv,
            (1..30).map { id -> testRunRow(id = id, rarityLevel = 2, word = "w$id") } +
                (31..60).map { id -> testRunRow(id = id, rarityLevel = 3, word = "w$id") }
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
                runSlug = "step5_pair_25_75",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 60,
                lowerRatio = 0.25,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 3L,
                transitions = listOf(LevelTransition(fromLevel = 2, toLevel = 2, fromLevelUpper = 3)),
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level2Count = rows.count { it["final_level"] == "2" }
        val level3Count = rows.count { it["final_level"] == "3" }
        assertEquals(15, level2Count)
        assertEquals(45, level3Count)
    }

    @Test
    fun rebalance_pair_transition_can_be_reapplied_in_loop_runs() {
        val inputCsv = tempDir.resolve("step2_run_loop_levels_2_3.csv")
        val intermediateCsv = tempDir.resolve("step5_loop_1.csv")
        val outputCsv = tempDir.resolve("step5_loop_2.csv")

        repo.appendRunRows(
            inputCsv,
            (1..50).map { id -> testRunRow(id = id, rarityLevel = 2, word = "w$id") } +
                (51..100).map { id -> testRunRow(id = id, rarityLevel = 3, word = "w$id") }
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 3,
                tag = "uncertain",
                confidence = 0.5
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        val transition = listOf(LevelTransition(fromLevel = 2, toLevel = 2, fromLevelUpper = 3))

        step5.execute(
            Step5Options(
                runSlug = "step5_loop_1",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = intermediateCsv,
                batchSize = 100,
                lowerRatio = 0.25,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 4L,
                transitions = transition,
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_loop_2",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = intermediateCsv,
                outputCsvPath = outputCsv,
                batchSize = 100,
                lowerRatio = 0.25,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 5L,
                transitions = transition,
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level2Count = rows.count { it["final_level"] == "2" }
        val level3Count = rows.count { it["final_level"] == "3" }
        assertEquals(25, level2Count)
        assertEquals(75, level3Count)
    }

    @Test
    fun rebalance_pair_transition_preserves_initial_source_mix_per_batch() {
        val inputCsv = tempDir.resolve("step2_run_mix_25_75.csv")
        val outputCsv = tempDir.resolve("step5_mix_25_75.csv")

        repo.appendRunRows(
            inputCsv,
            (1..25).map { id -> testRunRow(id = id, rarityLevel = 2, word = "w$id") } +
                (26..100).map { id -> testRunRow(id = id, rarityLevel = 3, word = "w$id") }
        )

        val seenBatchIds = mutableListOf<List<Int>>()
        val lm = object : LmClient {
            override fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
                return ResolvedEndpoint(
                    endpoint = "http://127.0.0.1:1234/v1/chat/completions",
                    modelsEndpoint = "http://127.0.0.1:1234/v1/models",
                    flavor = LmApiFlavor.OPENAI_COMPAT,
                    source = "test"
                )
            }

            override fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) {
                // no-op
            }

            override fun scoreBatchResilient(batch: List<BaseWordRow>, context: ScoringContext): List<ScoreResult> {
                seenBatchIds += batch.map { it.wordId }
                return batch.map { row ->
                    ScoreResult(
                        wordId = row.wordId,
                        word = row.word,
                        type = row.type,
                        rarityLevel = 3,
                        tag = "uncertain",
                        confidence = 0.5
                    )
                }
            }
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_mix_25_75",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 20,
                lowerRatio = 1.0 / 3.0,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 6L,
                transitions = listOf(LevelTransition(fromLevel = 2, toLevel = 2, fromLevelUpper = 3)),
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        assertEquals(5, seenBatchIds.size)
        seenBatchIds.forEach { batchIds ->
            val sourceLevel2 = batchIds.count { it <= 25 }
            val sourceLevel3 = batchIds.count { it >= 26 }
            assertEquals(5, sourceLevel2)
            assertEquals(15, sourceLevel3)
        }
    }

    @Test
    fun rebalance_keeps_distribution_when_batch_already_matches_target_count() {
        val inputCsv = tempDir.resolve("step2_run_exact_target_mix.csv")
        val outputCsv = tempDir.resolve("step5_exact_target_mix.csv")

        repo.appendRunRows(
            inputCsv,
            (1..24).map { id -> testRunRow(id = id, rarityLevel = 3, word = "w$id") } +
                (25..60).map { id -> testRunRow(id = id, rarityLevel = 4, word = "w$id") }
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 4, // opposite signal; should be ignored when already on exact target
                tag = "rare",
                confidence = 0.2
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        step5.execute(
            Step5Options(
                runSlug = "step5_exact_target_mix",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 60,
                lowerRatio = 0.4, // 24 of 60 should stay at level 3
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 10L,
                transitions = listOf(LevelTransition(fromLevel = 3, toLevel = 3, fromLevelUpper = 4)),
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level3Count = rows.count { it["final_level"] == "3" }
        val level4Count = rows.count { it["final_level"] == "4" }
        assertEquals(24, level3Count)
        assertEquals(36, level4Count)
        assertEquals(1, lm.scoreCalls)
    }

    @Test
    fun rebalance_uses_cumulative_target_compensation_across_batches() {
        val inputCsv = tempDir.resolve("step2_run_125.csv")
        val outputCsv = tempDir.resolve("step5_run_125.csv")

        repo.appendRunRows(
            inputCsv,
            (1..125).map { id -> testRunRow(id = id, rarityLevel = 2, word = "w$id") }
        )

        val lm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 3,
                tag = "uncertain",
                confidence = 0.5
            )
        }

        val step5 = RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = lm,
            outputDir = tempDir.resolve("build/rarity")
        )

        val ratio = 0.252
        step5.execute(
            Step5Options(
                runSlug = "step5_cumulative_ratio",
                model = MODEL_GPT_OSS_20B,
                inputCsvPath = inputCsv,
                outputCsvPath = outputCsv,
                batchSize = 20,
                lowerRatio = ratio,
                maxRetries = 1,
                timeoutSeconds = 20,
                maxTokens = 600,
                skipPreflight = true,
                endpointOption = null,
                baseUrlOption = null,
                seed = 7L,
                transitions = listOf(LevelTransition(fromLevel = 2, toLevel = 1)),
                systemPrompt = testSystemPrompt,
                userTemplate = testUserTemplate
            )
        )

        val rows = repo.readTable(outputCsv).toRowMaps()
        val level1Count = rows.count { it["final_level"] == "1" }
        val expected = kotlin.math.round(125 * ratio).toInt()
        assertEquals(expected, level1Count)
    }

    @Test
    fun rebalance_resumes_from_checkpoint_per_batch() {
        val inputCsv = tempDir.resolve("step2_run_resume.csv")
        val outputCsv = tempDir.resolve("step5_resume.csv")
        val outputDir = tempDir.resolve("build/rarity")

        repo.appendRunRows(
            inputCsv,
            (1..12).map { id -> testRunRow(id = id, rarityLevel = 2, word = "w$id") }
        )

        val firstLm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 2,
                tag = "uncertain",
                confidence = 0.5
            )
        }

        val options = Step5Options(
            runSlug = "step5_resume_batches",
            model = MODEL_GPT_OSS_20B,
            inputCsvPath = inputCsv,
            outputCsvPath = outputCsv,
            batchSize = 3,
            lowerRatio = 1.0 / 3.0,
            maxRetries = 1,
            timeoutSeconds = 20,
            maxTokens = 600,
            skipPreflight = true,
            endpointOption = null,
            baseUrlOption = null,
            seed = 8L,
            transitions = listOf(LevelTransition(fromLevel = 2, toLevel = 1)),
            systemPrompt = testSystemPrompt,
            userTemplate = testUserTemplate
        )

        RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = firstLm,
            outputDir = outputDir
        ).execute(options)

        assertEquals(4, firstLm.scoreCalls)
        val checkpointPath = outputDir.resolve("rebalance/checkpoints/step5_resume_batches.checkpoint.jsonl")
        assertTrue(Files.exists(checkpointPath))
        assertEquals(4, Files.readAllLines(checkpointPath).size)

        val secondLm = FakeLmClient {
            ScoreResult(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = 2,
                tag = "uncertain",
                confidence = 0.5
            )
        }

        RarityStep5Rebalancer(
            runCsvRepository = repo,
            lmClient = secondLm,
            outputDir = outputDir
        ).execute(options)

        assertEquals(0, secondLm.scoreCalls)
    }
}
