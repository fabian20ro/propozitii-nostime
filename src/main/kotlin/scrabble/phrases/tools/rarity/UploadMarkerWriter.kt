package scrabble.phrases.tools.rarity

import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Path
import java.time.OffsetDateTime

class UploadMarkerWriter(
    private val runCsvRepository: RunCsvRepository
) {

    fun markUploadedRows(
        finalCsvPath: Path,
        uploadedLevels: Map<Int, Int>,
        statusByWordId: Map<Int, String>,
        uploadBatchId: String,
        uploadedAt: String = OffsetDateTime.now().toString()
    ): UploadMarkerResult {
        if (statusByWordId.isEmpty()) {
            return UploadMarkerResult(markerPath = finalCsvPath, usedCompanionFile = false, markedRows = 0)
        }

        return try {
            markInPlace(finalCsvPath, uploadedLevels, statusByWordId, uploadBatchId, uploadedAt)
        } catch (e: Exception) {
            if (!isReadOnlyWriteFailure(e)) {
                throw e
            }
            writeCompanion(finalCsvPath, uploadedLevels, statusByWordId, uploadBatchId, uploadedAt)
        }
    }

    private fun markInPlace(
        finalCsvPath: Path,
        uploadedLevels: Map<Int, Int>,
        statusByWordId: Map<Int, String>,
        uploadBatchId: String,
        uploadedAt: String
    ): UploadMarkerResult {
        val table = runCsvRepository.readTable(finalCsvPath)
        require(table.headers.contains("word_id")) {
            "CSV ${finalCsvPath.toAbsolutePath()} is missing required column 'word_id'"
        }

        val headers = table.headers + UPLOAD_MARKER_HEADERS.filterNot(table.headers::contains)
        var markedRows = 0

        val rows = table.records.map { record ->
            val row = table.headers.zip(record.values).toMap().toMutableMap()
            val wordId = row["word_id"]?.toIntOrNull()
            val status = wordId?.let(statusByWordId::get)

            if (status != null) {
                row["uploaded_at"] = uploadedAt
                row["uploaded_level"] = wordId?.let(uploadedLevels::get)?.toString().orEmpty()
                row["upload_status"] = status
                row["upload_batch_id"] = uploadBatchId
                markedRows++
            } else {
                ensureMarkerKeys(row)
            }

            headers.map { header -> row[header].orEmpty() }
        }

        runCsvRepository.writeTableAtomic(finalCsvPath, headers, rows)
        return UploadMarkerResult(markerPath = finalCsvPath, usedCompanionFile = false, markedRows = markedRows)
    }

    private fun ensureMarkerKeys(row: MutableMap<String, String>) {
        if (!row.containsKey("uploaded_at")) row["uploaded_at"] = ""
        if (!row.containsKey("uploaded_level")) row["uploaded_level"] = ""
        if (!row.containsKey("upload_status")) row["upload_status"] = ""
        if (!row.containsKey("upload_batch_id")) row["upload_batch_id"] = ""
    }

    private fun writeCompanion(
        finalCsvPath: Path,
        uploadedLevels: Map<Int, Int>,
        statusByWordId: Map<Int, String>,
        uploadBatchId: String,
        uploadedAt: String
    ): UploadMarkerResult {
        val companionPath = finalCsvPath.resolveSibling("${finalCsvPath.fileName}.upload_markers.csv")
        val rows = statusByWordId.entries
            .sortedBy { it.key }
            .map { (wordId, status) ->
                listOf(
                    wordId.toString(),
                    uploadedAt,
                    uploadedLevels[wordId]?.toString().orEmpty(),
                    status,
                    uploadBatchId
                )
            }

        runCsvRepository.writeRows(companionPath, listOf("word_id") + UPLOAD_MARKER_HEADERS, rows)
        return UploadMarkerResult(markerPath = companionPath, usedCompanionFile = true, markedRows = rows.size)
    }

    private fun isReadOnlyWriteFailure(e: Exception): Boolean {
        return when (e) {
            is AccessDeniedException -> true
            is FileSystemException -> e.reason?.lowercase()?.contains("read-only") == true
            else -> false
        }
    }
}
