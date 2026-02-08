package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Step3ComparatorTest {

    @TempDir
    lateinit var tempDir: Path

    private val repo = RunCsvRepository()

    private fun writeBaseCsv(words: List<Triple<Int, String, String>>): Path {
        val path = tempDir.resolve("base.csv")
        repo.writeRows(path, BASE_CSV_HEADERS, words.map { (id, word, type) ->
            listOf(id.toString(), word, type)
        })
        return path
    }

    private fun writeRunCsv(name: String, rows: List<RunCsvRow>): Path {
        val path = tempDir.resolve("$name.csv")
        if (rows.isEmpty()) {
            repo.writeRows(path, RUN_CSV_HEADERS, emptyList())
            return path
        }
        repo.appendRunRows(path, rows)
        return path
    }

    private fun executeAndRead(
        base: List<Triple<Int, String, String>>,
        runARows: List<RunCsvRow>,
        runBRows: List<RunCsvRow>,
        runCRows: List<RunCsvRow> = emptyList(),
        outlierThreshold: Int = DEFAULT_OUTLIER_THRESHOLD,
        confidenceThreshold: Double = DEFAULT_CONFIDENCE_THRESHOLD,
        mergeStrategy: Step3MergeStrategy = DEFAULT_STEP3_MERGE_STRATEGY
    ): Pair<List<Map<String, String>>, List<Map<String, String>>> {
        val basePath = writeBaseCsv(base)
        val runAPath = writeRunCsv("run_a", runARows)
        val runBPath = writeRunCsv("run_b", runBRows)
        val runCPath = writeRunCsv("run_c", runCRows)
        val outputPath = tempDir.resolve("comparison.csv")
        val outliersPath = tempDir.resolve("outliers.csv")

        val comparator = RarityStep3Comparator(repo)
        comparator.execute(
            Step3Options(
                runACsvPath = runAPath,
                runBCsvPath = runBPath,
                runCCsvPath = runCPath,
                outputCsvPath = outputPath,
                outliersCsvPath = outliersPath,
                baseCsvPath = basePath,
                outlierThreshold = outlierThreshold,
                confidenceThreshold = confidenceThreshold,
                mergeStrategy = mergeStrategy
            )
        )

        val comparisonRows = repo.readTable(outputPath).toRowMaps()
        val outlierRows = repo.readTable(outliersPath).toRowMaps()
        return comparisonRows to outlierRows
    }

    @Test
    fun both_runs_agree_produces_no_outliers() {
        val base = listOf(Triple(1, "apa", "N"), Triple(2, "brad", "N"))
        val runA = listOf(
            testRunRow(1, rarityLevel = 3, confidence = 0.8, runSlug = "a"),
            testRunRow(2, rarityLevel = 4, confidence = 0.9, runSlug = "a")
        )
        val runB = listOf(
            testRunRow(1, rarityLevel = 3, confidence = 0.8, runSlug = "b"),
            testRunRow(2, rarityLevel = 4, confidence = 0.9, runSlug = "b")
        )

        val (comparison, outliers) = executeAndRead(base, runA, runB)

        assertEquals(2, comparison.size)
        assertEquals(0, outliers.size)

        val row1 = comparison.first { it["word_id"] == "1" }
        assertEquals("3", row1["median_level"])
        assertEquals("0", row1["spread"])
        assertEquals("false", row1["is_outlier"])
        assertEquals("3", row1["final_level"])
        assertEquals("median", row1["merge_rule"])
    }

    @Test
    fun large_spread_marks_outlier() {
        val base = listOf(Triple(1, "fulger", "N"))
        val runA = listOf(testRunRow(1, rarityLevel = 2, confidence = 0.9, runSlug = "a"))
        val runB = listOf(testRunRow(1, rarityLevel = 5, confidence = 0.9, runSlug = "b"))

        val (comparison, outliers) = executeAndRead(base, runA, runB, outlierThreshold = 2)

        assertEquals(1, comparison.size)
        assertEquals(1, outliers.size)

        val row = comparison.first()
        assertEquals("3", row["spread"])
        assertTrue(row["is_outlier"] == "true")
        assertTrue(row["reason"]!!.contains("spread>=2"))

        val outlier = outliers.first()
        assertEquals("1", outlier["word_id"])
        assertEquals("fulger", outlier["word"])
    }

    @Test
    fun low_confidence_marks_outlier() {
        val base = listOf(Triple(1, "cerb", "N"))
        val runA = listOf(testRunRow(1, rarityLevel = 3, confidence = 0.4, runSlug = "a"))
        val runB = listOf(testRunRow(1, rarityLevel = 3, confidence = 0.8, runSlug = "b"))

        val (comparison, outliers) = executeAndRead(base, runA, runB, confidenceThreshold = 0.55)

        assertEquals(1, comparison.size)
        assertEquals(1, outliers.size)

        val row = comparison.first()
        assertEquals("0", row["spread"])
        assertTrue(row["is_outlier"] == "true")
        assertTrue(row["reason"]!!.contains("low_confidence"))
    }

    @Test
    fun missing_run_b_uses_single_run_level_and_no_outlier() {
        val base = listOf(Triple(1, "deal", "N"))
        val runA = listOf(testRunRow(1, rarityLevel = 4, confidence = 0.9, runSlug = "a"))
        val runB = emptyList<RunCsvRow>()

        val (comparison, outliers) = executeAndRead(base, runA, runB)

        assertEquals(1, comparison.size)
        assertEquals(0, outliers.size)

        val row = comparison.first()
        assertEquals("4", row["median_level"])
        assertEquals("0", row["spread"])
        assertFalse(row["is_outlier"] == "true")
        assertEquals("", row["run_b_level"])
    }

    @Test
    fun both_runs_missing_uses_fallback_level() {
        val base = listOf(Triple(1, "echidna", "N"))
        val runA = emptyList<RunCsvRow>()
        val runB = emptyList<RunCsvRow>()

        val (comparison, outliers) = executeAndRead(base, runA, runB)

        assertEquals(1, comparison.size)
        assertEquals(0, outliers.size)

        val row = comparison.first()
        assertEquals(FALLBACK_RARITY_LEVEL.toString(), row["median_level"])
        assertEquals(FALLBACK_RARITY_LEVEL.toString(), row["final_level"])
        assertEquals("0", row["spread"])
        assertEquals("", row["run_a_level"])
        assertEquals("", row["run_b_level"])
    }

    @Test
    fun any_extremes_strategy_uses_level_1_if_present_in_any_run() {
        val base = listOf(Triple(1, "masa", "N"))
        val runA = listOf(testRunRow(1, rarityLevel = 4, confidence = 0.8, runSlug = "a"))
        val runB = listOf(testRunRow(1, rarityLevel = 1, confidence = 0.8, runSlug = "b"))
        val runC = listOf(testRunRow(1, rarityLevel = 3, confidence = 0.8, runSlug = "c"))

        val (comparison, _) = executeAndRead(
            base = base,
            runARows = runA,
            runBRows = runB,
            runCRows = runC,
            mergeStrategy = Step3MergeStrategy.ANY_EXTREMES
        )

        val row = comparison.single()
        assertEquals("3", row["median_level"])
        assertEquals("1", row["final_level"])
        assertEquals("any_level_1", row["merge_rule"])
    }

    @Test
    fun any_extremes_strategy_uses_level_2_when_median_is_three_or_more() {
        val base = listOf(Triple(1, "flaut", "N"))
        val runA = listOf(testRunRow(1, rarityLevel = 4, confidence = 0.8, runSlug = "a"))
        val runB = listOf(testRunRow(1, rarityLevel = 2, confidence = 0.8, runSlug = "b"))
        val runC = listOf(testRunRow(1, rarityLevel = 4, confidence = 0.8, runSlug = "c"))

        val (comparison, _) = executeAndRead(
            base = base,
            runARows = runA,
            runBRows = runB,
            runCRows = runC,
            mergeStrategy = Step3MergeStrategy.ANY_EXTREMES
        )

        val row = comparison.single()
        assertEquals("4", row["median_level"])
        assertEquals("2", row["final_level"])
        assertEquals("any_level_2_over_median", row["merge_rule"])
    }

    @Test
    fun any_extremes_strategy_uses_level_5_when_median_is_three_or_four() {
        val base = listOf(Triple(1, "zvop", "N"))
        val runA = listOf(testRunRow(1, rarityLevel = 4, confidence = 0.8, runSlug = "a"))
        val runB = listOf(testRunRow(1, rarityLevel = 5, confidence = 0.8, runSlug = "b"))
        val runC = listOf(testRunRow(1, rarityLevel = 4, confidence = 0.8, runSlug = "c"))

        val (comparison, _) = executeAndRead(
            base = base,
            runARows = runA,
            runBRows = runB,
            runCRows = runC,
            mergeStrategy = Step3MergeStrategy.ANY_EXTREMES
        )

        val row = comparison.single()
        assertEquals("4", row["median_level"])
        assertEquals("5", row["final_level"])
        assertEquals("any_level_5_over_median", row["merge_rule"])
    }
}
