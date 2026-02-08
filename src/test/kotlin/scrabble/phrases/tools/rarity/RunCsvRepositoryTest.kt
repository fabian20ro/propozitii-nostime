package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RunCsvRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private val repo = RunCsvRepository()

    @Test
    fun stale_in_memory_plus_new_disk_rows_does_not_drop_rows() {
        val path = tempDir.resolve("run.csv")
        repo.appendRunRows(path, listOf(testRunRow(1), testRunRow(2)))

        val inMemory = repo.loadRunRows(path)
        val baseline = repo.computeBaseline(inMemory)

        repo.appendRunRows(path, listOf(testRunRow(3)))

        repo.mergeAndRewriteAtomic(path, inMemory, baseline)

        val ids = repo.loadRunRows(path).map { it.wordId }
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun atomic_rewrite_preserves_all_ids_and_order() {
        val path = tempDir.resolve("rewrite.csv")
        repo.rewriteRunRowsAtomic(path, listOf(testRunRow(3), testRunRow(1), testRunRow(2)))

        val ids = repo.loadRunRows(path).map { it.wordId }
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun append_respects_existing_marker_columns() {
        val path = tempDir.resolve("marked.csv")
        repo.writeRows(
            path,
            RUN_CSV_HEADERS + UPLOAD_MARKER_HEADERS,
            listOf(
                listOf(
                    "1", "apa", "N", "2", "common", "0.9", "2026-02-08T00:00:00Z", "m", "r",
                    "2026-02-08T00:10:00Z", "2", "uploaded", "batch_1"
                )
            )
        )

        repo.appendRunRows(path, listOf(testRunRow(2, word = "brad")))

        val table = repo.readTable(path)
        assertEquals(2, table.records.size)
        assertEquals(RUN_CSV_HEADERS + UPLOAD_MARKER_HEADERS, table.headers)
        assertTrue(table.records.all { it.values.size == table.headers.size })

        val ids = repo.loadRunRows(path).map { it.wordId }
        assertEquals(listOf(1, 2), ids)
    }
}
