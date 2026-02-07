package scrabble.phrases.tools.rarity

import java.nio.file.Path
import java.time.OffsetDateTime

data class Step4Options(
    val finalCsvPath: Path,
    val mode: UploadMode,
    val reportPath: Path,
    val uploadBatchId: String?
)

private data class UploadPlan(
    val updates: Map<Int, Int>,
    val reportRows: List<List<String>>,
    val statusByWordId: Map<Int, String>
)

class RarityStep4Uploader(
    private val wordStore: WordStore,
    private val runCsvRepository: RunCsvRepository,
    private val uploadMarkerWriter: UploadMarkerWriter
) {

    fun execute(options: Step4Options) {
        val finalLevels = runCsvRepository.loadFinalLevels(options.finalCsvPath)
        val dbLevels = wordStore.fetchAllWordLevels().associateBy { it.wordId }

        val plan = buildUploadPlan(options.mode, finalLevels, dbLevels)

        wordStore.updateRarityLevels(plan.updates)
        runCsvRepository.writeRows(options.reportPath, UPLOAD_REPORT_HEADERS, plan.reportRows)

        val markerResult = uploadMarkerWriter.markUploadedRows(
            finalCsvPath = options.finalCsvPath,
            uploadedLevels = plan.updates,
            statusByWordId = plan.statusByWordId,
            uploadBatchId = options.uploadBatchId ?: "upload_${System.currentTimeMillis()}",
            uploadedAt = OffsetDateTime.now().toString()
        )

        println("Step 4 complete. mode=${options.mode.name.lowercase()} updated=${plan.updates.size}")
        println("Upload report: ${options.reportPath.toAbsolutePath()}")
        println(
            "Upload markers: ${markerResult.markerPath.toAbsolutePath()} " +
                "(companion=${markerResult.usedCompanionFile}, marked_rows=${markerResult.markedRows})"
        )
    }

    private fun buildUploadPlan(
        mode: UploadMode,
        finalLevels: Map<Int, Int>,
        dbLevels: Map<Int, WordLevel>
    ): UploadPlan {
        return when (mode) {
            UploadMode.PARTIAL -> buildPartialPlan(finalLevels, dbLevels)
            UploadMode.FULL_FALLBACK -> buildFullFallbackPlan(finalLevels, dbLevels)
        }
    }

    private fun buildPartialPlan(
        finalLevels: Map<Int, Int>,
        dbLevels: Map<Int, WordLevel>
    ): UploadPlan {
        val updates = mutableMapOf<Int, Int>()
        val reportRows = mutableListOf<List<String>>()
        val statusByWordId = mutableMapOf<Int, String>()

        finalLevels.entries.sortedBy { it.key }.forEach { (wordId, level) ->
            val existing = dbLevels[wordId]
            if (existing == null) {
                reportRows += listOf(wordId.toString(), "", "", "missing_db_word")
                statusByWordId[wordId] = "missing_db_word"
                return@forEach
            }

            updates[wordId] = level
            reportRows += listOf(
                wordId.toString(),
                existing.rarityLevel.toString(),
                level.toString(),
                "final_csv"
            )
            statusByWordId[wordId] = "uploaded"
        }

        return UploadPlan(
            updates = updates,
            reportRows = reportRows,
            statusByWordId = statusByWordId
        )
    }

    private fun buildFullFallbackPlan(
        finalLevels: Map<Int, Int>,
        dbLevels: Map<Int, WordLevel>
    ): UploadPlan {
        val updates = mutableMapOf<Int, Int>()
        val reportRows = mutableListOf<List<String>>()

        dbLevels.values.sortedBy { it.wordId }.forEach { existing ->
            val fromFinalCsv = finalLevels.containsKey(existing.wordId)
            val newLevel = finalLevels[existing.wordId] ?: FALLBACK_RARITY_LEVEL
            val source = if (fromFinalCsv) "final_csv" else "fallback_4"

            updates[existing.wordId] = newLevel
            reportRows += listOf(
                existing.wordId.toString(),
                existing.rarityLevel.toString(),
                newLevel.toString(),
                source
            )
        }

        val statusByWordId = finalLevels.keys.associateWith { wordId ->
            if (dbLevels.containsKey(wordId)) "uploaded" else "missing_db_word"
        }

        return UploadPlan(
            updates = updates,
            reportRows = reportRows,
            statusByWordId = statusByWordId
        )
    }
}
