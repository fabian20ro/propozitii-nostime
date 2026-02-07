package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RunLockManagerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun second_writer_fails_when_lock_held() {
        val lockManager = RunLockManager()
        val path = tempDir.resolve("run.csv")

        val firstLock = lockManager.acquire(path)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                lockManager.acquire(path)
            }
        } finally {
            firstLock.close()
        }
    }
}
