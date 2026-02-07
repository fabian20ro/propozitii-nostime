package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Step4UploaderTest {

    @TempDir
    lateinit var tempDir: Path

    private val repo = RunCsvRepository()

    @Test
    fun partial_mode_updates_only_ids_in_final_csv() {
        val store = InMemoryWordStore(mapOf(1 to 1, 2 to 2, 3 to 3))
        val finalCsv = tempDir.resolve("final_partial.csv")
        val report = tempDir.resolve("report_partial.csv")
        repo.writeRows(
            finalCsv,
            listOf("word_id", "final_level"),
            listOf(
                listOf("1", "5"),
                listOf("99", "2")
            )
        )

        createUploader(store).execute(
            Step4Options(
                finalCsvPath = finalCsv,
                mode = UploadMode.PARTIAL,
                reportPath = report,
                uploadBatchId = "batch_partial"
            )
        )

        assertEquals(5, store.levels[1])
        assertEquals(2, store.levels[2])
        assertEquals(3, store.levels[3])

        val reportRows = repo.readTable(report).toRowMaps()
        assertTrue(reportRows.any { it["word_id"] == "1" && it["source"] == "final_csv" })
        assertTrue(reportRows.any { it["word_id"] == "99" && it["source"] == "missing_db_word" })
    }

    @Test
    fun full_fallback_mode_preserves_legacy_behavior() {
        val store = InMemoryWordStore(mapOf(1 to 1, 2 to 2, 3 to 3))
        val finalCsv = tempDir.resolve("final_full.csv")
        val report = tempDir.resolve("report_full.csv")
        repo.writeRows(
            finalCsv,
            listOf("word_id", "final_level"),
            listOf(listOf("1", "5"))
        )

        createUploader(store).execute(
            Step4Options(
                finalCsvPath = finalCsv,
                mode = UploadMode.FULL_FALLBACK,
                reportPath = report,
                uploadBatchId = "batch_full"
            )
        )

        assertEquals(5, store.levels[1])
        assertEquals(4, store.levels[2])
        assertEquals(4, store.levels[3])

        val rows = repo.readTable(report).toRowMaps()
        assertTrue(rows.any { it["word_id"] == "2" && it["source"] == "fallback_4" })
        assertTrue(rows.any { it["word_id"] == "3" && it["source"] == "fallback_4" })
    }

    @Test
    fun marks_uploaded_rows_in_csv() {
        val store = InMemoryWordStore(mapOf(1 to 1, 2 to 2))
        val finalCsv = tempDir.resolve("final_markers.csv")
        val report = tempDir.resolve("report_markers.csv")
        repo.writeRows(
            finalCsv,
            listOf("word_id", "final_level"),
            listOf(
                listOf("1", "5"),
                listOf("2", "3")
            )
        )

        createUploader(store).execute(
            Step4Options(
                finalCsvPath = finalCsv,
                mode = UploadMode.PARTIAL,
                reportPath = report,
                uploadBatchId = "batch_mark"
            )
        )

        val table = repo.readTable(finalCsv)
        val rows = table.toRowMaps()

        assertTrue(table.headers.containsAll(UPLOAD_MARKER_HEADERS))
        assertTrue(rows.all { it["upload_status"] == "uploaded" })
        assertTrue(rows.any { it["word_id"] == "1" && it["uploaded_level"] == "5" })
    }

    private fun createUploader(store: WordStore): RarityStep4Uploader {
        return RarityStep4Uploader(store, repo, UploadMarkerWriter(repo))
    }
}
