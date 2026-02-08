package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class UploadMarkerWriterTest {

    @TempDir
    lateinit var tempDir: Path

    private val repo = RunCsvRepository()
    private val writer = UploadMarkerWriter(repo)

    private fun writeFinalCsv(rows: List<List<String>>): Path {
        val path = tempDir.resolve("final.csv")
        val headers = COMPARISON_CSV_HEADERS
        repo.writeRows(path, headers, rows)
        return path
    }

    private fun comparisonRow(wordId: Int, finalLevel: Int): List<String> {
        val row = mutableMapOf(
            "word_id" to wordId.toString(),
            "word" to "word_$wordId",
            "type" to "N",
            "run_a_level" to finalLevel.toString(),
            "run_a_confidence" to "0.8",
            "run_b_level" to finalLevel.toString(),
            "run_b_confidence" to "0.8",
            "run_c_level" to "",
            "run_c_confidence" to "",
            "median_level" to finalLevel.toString(),
            "spread" to "0",
            "is_outlier" to "false",
            "reason" to "",
            "merge_strategy" to "median",
            "merge_rule" to "median",
            "final_level" to finalLevel.toString()
        )
        return COMPARISON_CSV_HEADERS.map { header -> row[header].orEmpty() }
    }

    @Test
    fun marks_uploaded_rows_in_place() {
        val finalCsv = writeFinalCsv(listOf(
            comparisonRow(1, 3),
            comparisonRow(2, 4)
        ))

        val result = writer.markUploadedRows(
            finalCsvPath = finalCsv,
            uploadedLevels = mapOf(1 to 3, 2 to 4),
            statusByWordId = mapOf(1 to "updated", 2 to "unchanged"),
            uploadBatchId = "batch_001",
            uploadedAt = "2026-02-08T00:00:00Z"
        )

        assertFalse(result.usedCompanionFile)
        assertEquals(2, result.markedRows)
        assertEquals(finalCsv, result.markerPath)

        val table = repo.readTable(finalCsv)
        assertTrue(table.headers.containsAll(UPLOAD_MARKER_HEADERS))

        val rowMaps = table.toRowMaps()
        val row1 = rowMaps.first { it["word_id"] == "1" }
        assertEquals("updated", row1["upload_status"])
        assertEquals("batch_001", row1["upload_batch_id"])
        assertEquals("3", row1["uploaded_level"])

        val row2 = rowMaps.first { it["word_id"] == "2" }
        assertEquals("unchanged", row2["upload_status"])
    }

    @Test
    fun empty_status_map_returns_zero_marked_rows() {
        val finalCsv = writeFinalCsv(listOf(comparisonRow(1, 3)))

        val result = writer.markUploadedRows(
            finalCsvPath = finalCsv,
            uploadedLevels = emptyMap(),
            statusByWordId = emptyMap(),
            uploadBatchId = "batch_empty"
        )

        assertEquals(0, result.markedRows)
        assertFalse(result.usedCompanionFile)
    }

    @Test
    fun unmarked_rows_get_empty_marker_columns() {
        val finalCsv = writeFinalCsv(listOf(
            comparisonRow(1, 3),
            comparisonRow(2, 4)
        ))

        val result = writer.markUploadedRows(
            finalCsvPath = finalCsv,
            uploadedLevels = mapOf(1 to 3),
            statusByWordId = mapOf(1 to "updated"),
            uploadBatchId = "batch_partial",
            uploadedAt = "2026-02-08T00:00:00Z"
        )

        assertEquals(1, result.markedRows)

        val rowMaps = repo.readTable(finalCsv).toRowMaps()
        val row2 = rowMaps.first { it["word_id"] == "2" }
        assertEquals("", row2["upload_status"])
        assertEquals("", row2["upload_batch_id"])
        assertEquals("", row2["uploaded_level"])
    }
}
