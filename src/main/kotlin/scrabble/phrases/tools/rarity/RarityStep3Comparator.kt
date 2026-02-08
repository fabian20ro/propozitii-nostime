package scrabble.phrases.tools.rarity

import java.nio.file.Path

data class Step3Options(
    val runACsvPath: Path,
    val runBCsvPath: Path,
    val runCCsvPath: Path?,
    val outputCsvPath: Path,
    val outliersCsvPath: Path,
    val baseCsvPath: Path,
    val outlierThreshold: Int,
    val confidenceThreshold: Double,
    val mergeStrategy: Step3MergeStrategy
)

class RarityStep3Comparator(
    private val runCsvRepository: RunCsvRepository
) {

    fun execute(options: Step3Options) {
        val baseRows = runCsvRepository.loadBaseRows(options.baseCsvPath)
        val runA = runCsvRepository.loadRunRows(options.runACsvPath).associateBy { it.wordId }
        val runB = runCsvRepository.loadRunRows(options.runBCsvPath).associateBy { it.wordId }
        val runC = options.runCCsvPath
            ?.let { runCsvRepository.loadRunRows(it).associateBy { row -> row.wordId } }
            ?: emptyMap()

        val comparisonRows = mutableListOf<List<String>>()
        val outlierRows = mutableListOf<List<String>>()
        val finalDistribution = RarityDistribution.empty()

        baseRows.forEach { base ->
            val row = buildComparisonRow(base, runA[base.wordId], runB[base.wordId], runC[base.wordId], options)
            comparisonRows += row.toCsv()
            finalDistribution.increment(row.finalLevel)
            if (row.isOutlier) {
                outlierRows += row.toOutlierCsv()
            }
        }

        runCsvRepository.writeRows(options.outputCsvPath, COMPARISON_CSV_HEADERS, comparisonRows)
        runCsvRepository.writeRows(options.outliersCsvPath, OUTLIERS_CSV_HEADERS, outlierRows)

        println("Step 3 complete. Outliers=${outlierRows.size}")
        println("Step 3 final ${finalDistribution.format()}")
        println("Comparison: ${options.outputCsvPath.toAbsolutePath()}")
        println("Outliers: ${options.outliersCsvPath.toAbsolutePath()}")
    }

    private fun buildComparisonRow(
        base: BaseWordRow,
        runA: RunCsvRow?,
        runB: RunCsvRow?,
        runC: RunCsvRow?,
        options: Step3Options
    ): ComparisonRow {
        val levels = listOfNotNull(runA?.rarityLevel, runB?.rarityLevel, runC?.rarityLevel)
        val medianLevel = if (levels.isEmpty()) FALLBACK_RARITY_LEVEL else median(levels)
        val spread = if (levels.size < 2) 0 else levels.max() - levels.min()

        val lowConfidence = listOfNotNull(runA?.confidence, runB?.confidence, runC?.confidence)
            .any { it < options.confidenceThreshold }
        val isOutlier = levels.size >= 2 && (spread >= options.outlierThreshold || lowConfidence)

        val reasons = mutableListOf<String>()
        if (spread >= options.outlierThreshold) reasons += "spread>=${options.outlierThreshold}"
        if (lowConfidence) reasons += "low_confidence<${options.confidenceThreshold}"
        val mergeDecision = resolveFinalLevel(levels, medianLevel, options.mergeStrategy)

        return ComparisonRow(
            wordId = base.wordId,
            word = base.word,
            type = base.type,
            runALevel = runA?.rarityLevel,
            runAConfidence = runA?.confidence,
            runBLevel = runB?.rarityLevel,
            runBConfidence = runB?.confidence,
            runCLevel = runC?.rarityLevel,
            runCConfidence = runC?.confidence,
            medianLevel = medianLevel,
            spread = spread,
            isOutlier = isOutlier,
            reason = reasons.joinToString(";"),
            mergeStrategy = options.mergeStrategy,
            mergeRule = mergeDecision.rule,
            finalLevel = mergeDecision.level
        )
    }

    private data class MergeDecision(
        val level: Int,
        val rule: String
    )

    private fun resolveFinalLevel(
        levels: List<Int>,
        medianLevel: Int,
        strategy: Step3MergeStrategy
    ): MergeDecision {
        if (strategy == Step3MergeStrategy.MEDIAN) {
            return MergeDecision(level = medianLevel, rule = "median")
        }

        // Priority order for ANY_EXTREMES:
        // 1) any model says level 1 -> final 1
        // 2) if median is 3+ and any model says 2 -> final 2
        // 3) if median is 3/4 and any model says 5 -> final 5
        if (levels.any { it == 1 }) {
            return MergeDecision(level = 1, rule = "any_level_1")
        }
        if (medianLevel >= 3 && levels.any { it == 2 }) {
            return MergeDecision(level = 2, rule = "any_level_2_over_median")
        }
        if (medianLevel in 3..4 && levels.any { it == 5 }) {
            return MergeDecision(level = 5, rule = "any_level_5_over_median")
        }

        return MergeDecision(level = medianLevel, rule = "median_fallback")
    }

    private fun ComparisonRow.toCsv(): List<String> {
        return listOf(
            wordId.toString(),
            word,
            type,
            runALevel?.toString().orEmpty(),
            runAConfidence?.toString().orEmpty(),
            runBLevel?.toString().orEmpty(),
            runBConfidence?.toString().orEmpty(),
            runCLevel?.toString().orEmpty(),
            runCConfidence?.toString().orEmpty(),
            medianLevel.toString(),
            spread.toString(),
            isOutlier.toString(),
            reason,
            mergeStrategy.name.lowercase(),
            mergeRule,
            finalLevel.toString()
        )
    }

    private fun ComparisonRow.toOutlierCsv(): List<String> {
        return listOf(
            wordId.toString(),
            word,
            type,
            runALevel?.toString().orEmpty(),
            runBLevel?.toString().orEmpty(),
            runCLevel?.toString().orEmpty(),
            spread.toString(),
            reason
        )
    }
}
