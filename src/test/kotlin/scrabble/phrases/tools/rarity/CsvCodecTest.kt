package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
}
