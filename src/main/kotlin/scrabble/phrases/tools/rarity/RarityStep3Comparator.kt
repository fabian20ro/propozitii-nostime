package scrabble.phrases.tools.rarity

import java.nio.file.Path

data class Step3Options(
    val runACsvPath: Path,
    val runBCsvPath: Path,
    val outputCsvPath: Path,
    val outliersCsvPath: Path,
    val baseCsvPath: Path,
    val outlierThreshold: Int,
    val confidenceThreshold: Double
)

class RarityStep3Comparator(
    private val runCsvRepository: RunCsvRepository
) {

    fun execute(options: Step3Options) {
        val baseRows = runCsvRepository.loadBaseRows(options.baseCsvPath)
        val runA = runCsvRepository.loadRunRows(options.runACsvPath).associateBy { it.wordId }
        val runB = runCsvRepository.loadRunRows(options.runBCsvPath).associateBy { it.wordId }

        val comparisonRows = mutableListOf<List<String>>()
        val outlierRows = mutableListOf<List<String>>()

        baseRows.forEach { base ->
            val row = buildComparisonRow(base, runA[base.wordId], runB[base.wordId], options)
            comparisonRows += row.toCsv()
            if (row.isOutlier) {
                outlierRows += row.toOutlierCsv()
            }
        }

        runCsvRepository.writeRows(options.outputCsvPath, COMPARISON_CSV_HEADERS, comparisonRows)
        runCsvRepository.writeRows(options.outliersCsvPath, OUTLIERS_CSV_HEADERS, outlierRows)

        println("Step 3 complete. Outliers=${outlierRows.size}")
        println("Comparison: ${options.outputCsvPath.toAbsolutePath()}")
        println("Outliers: ${options.outliersCsvPath.toAbsolutePath()}")
    }

    private fun buildComparisonRow(
        base: BaseWordRow,
        runA: RunCsvRow?,
        runB: RunCsvRow?,
        options: Step3Options
    ): ComparisonRow {
        val levels = listOfNotNull(runA?.rarityLevel, runB?.rarityLevel)
        val medianLevel = if (levels.isEmpty()) FALLBACK_RARITY_LEVEL else median(levels)
        val spread = if (levels.size < 2) 0 else levels.max() - levels.min()

        val lowConfidence = listOfNotNull(runA?.confidence, runB?.confidence)
            .any { it < options.confidenceThreshold }
        val isOutlier = levels.size >= 2 && (spread >= options.outlierThreshold || lowConfidence)

        val reasons = mutableListOf<String>()
        if (spread >= options.outlierThreshold) reasons += "spread>=${options.outlierThreshold}"
        if (lowConfidence) reasons += "low_confidence<${options.confidenceThreshold}"

        return ComparisonRow(
            wordId = base.wordId,
            word = base.word,
            type = base.type,
            runALevel = runA?.rarityLevel,
            runAConfidence = runA?.confidence,
            runBLevel = runB?.rarityLevel,
            runBConfidence = runB?.confidence,
            medianLevel = medianLevel,
            spread = spread,
            isOutlier = isOutlier,
            reason = reasons.joinToString(";"),
            finalLevel = medianLevel
        )
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
            medianLevel.toString(),
            spread.toString(),
            isOutlier.toString(),
            reason,
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
            spread.toString(),
            reason
        )
    }
}
