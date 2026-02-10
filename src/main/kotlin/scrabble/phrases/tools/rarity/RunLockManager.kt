package scrabble.phrases.tools.rarity

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class RunLockManager {
    fun acquire(outputCsvPath: Path): AutoCloseable {
        outputCsvPath.parent?.let { Files.createDirectories(it) }
        val lockPath = outputCsvPath.resolveSibling("${outputCsvPath.fileName}.lock")
        lockPath.parent?.let { Files.createDirectories(it) }

        val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = tryAcquire(channel)
        if (lock == null) {
            channel.close()
            error(
                "Another step2 process is already writing to ${outputCsvPath.toAbsolutePath()}. " +
                    "Use a different --output-csv or stop the other process first."
            )
        }

        return AutoCloseable {
            try {
                lock.release()
            } finally {
                channel.close()
            }
        }
    }

    private fun tryAcquire(channel: FileChannel): FileLock? {
        return try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
    }
}
