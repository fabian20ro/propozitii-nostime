package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CsvCodecTest {

    @TempDir
    lateinit var tempDir: Path

    private val csv = CsvCodec()

    @Test
    fun quotes_commas_utf8_roundtrip() {
        val path = tempDir.resolve("roundtrip.csv")
        val headers = listOf("word_id", "word", "type")
        val rows = listOf(
            listOf("1", "mămăligă, \"caldă\"", "N"),
            listOf("2", "țărm", "N")
        )

        csv.writeTable(path, headers, rows)
        val read = csv.readTable(path)

        assertEquals(headers, read.headers)
        assertEquals(rows, read.records.map { it.values })
    }

    @Test
    fun malformed_row_is_reported_not_silently_lost() {
        val path = tempDir.resolve("bad.csv")
        Files.writeString(
            path,
            "\"word_id\",\"word\"\n\"1\",\"abc\n"
        )

        assertThrows(CsvFormatException::class.java) {
            csv.readTable(path)
        }
    }

    @Test
    fun atomic_write_roundtrip() {
        val path = tempDir.resolve("atomic.csv")
        val headers = listOf("id", "name")
        val rows = listOf(
            listOf("1", "first"),
            listOf("2", "second")
        )

        csv.writeTableAtomic(path, headers, rows)
        val read = csv.readTable(path)

        assertEquals(headers, read.headers)
        assertEquals(rows, read.records.map { it.values })
        assertFalse(Files.exists(path.resolveSibling("${path.fileName}.tmp")))
    }

    @Test
    fun empty_file_throws() {
        val path = tempDir.resolve("empty.csv")
        Files.writeString(path, "")

        assertThrows(IllegalArgumentException::class.java) {
            csv.readTable(path)
        }
    }

    @Test
    fun column_count_mismatch_reports_line_number() {
        val path = tempDir.resolve("mismatch.csv")
        Files.writeString(
            path,
            "\"a\",\"b\",\"c\"\n\"1\",\"2\",\"3\"\n\"4\",\"5\"\n"
        )

        val ex = assertThrows(CsvFormatException::class.java) {
            csv.readTable(path)
        }
        assertTrue(ex.message!!.contains("line 3"))
    }
}
