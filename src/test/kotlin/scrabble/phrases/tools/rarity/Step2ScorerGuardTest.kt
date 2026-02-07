package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class Step2ScorerGuardTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun shrink_detection_aborts_rewrite() {
        val repo = RunCsvRepository()
        val path = tempDir.resolve("runs/guard.csv")
        repo.appendRunRows(path, listOf(testRunRow(1)))

        val baseline = RunBaseline(count = 2, minId = 1, maxId = 2)

        assertThrows(IllegalStateException::class.java) {
            repo.mergeAndRewriteAtomic(path, listOf(testRunRow(1)), baseline)
        }

        val ids = repo.loadRunRows(path).map { it.wordId }
        assertEquals(listOf(1), ids)
    }
}
