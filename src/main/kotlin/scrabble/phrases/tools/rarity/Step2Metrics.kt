package scrabble.phrases.tools.rarity

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks scoring metrics for Step 2 observability.
 *
 * Thread-safe via atomic counters. Provides real-time progress formatting
 * with words-per-minute, ETA, and error breakdown by category.
 */
class Step2Metrics {

    enum class ErrorCategory {
        TRUNCATED_JSON,
        DECIMAL_FORMAT,
        WORD_MISMATCH,
        MODEL_CRASH,
        MISSING_CONTENT,
        CONNECTIVITY,
        OTHER
    }

    private val startInstant: Instant = Instant.now()
    private val totalScored = AtomicInteger(0)
    private val totalFailed = AtomicInteger(0)
    private val totalBatches = AtomicInteger(0)
    private val successfulBatches = AtomicInteger(0)
    private val repairedJsonCount = AtomicInteger(0)
    private val fuzzyMatchCount = AtomicInteger(0)
    private val partialExtractionCount = AtomicInteger(0)
    private val errorCounts = ErrorCategory.entries.associateWith { AtomicInteger(0) }
    private val lastBatchSize = AtomicInteger(0)

    fun recordBatchResult(batchSize: Int, scoredCount: Int) {
        totalBatches.incrementAndGet()
        totalScored.addAndGet(scoredCount)
        totalFailed.addAndGet(batchSize - scoredCount)
        lastBatchSize.set(batchSize)
        if (scoredCount > 0) {
            successfulBatches.incrementAndGet()
        }
        if (scoredCount in 1 until batchSize) {
            partialExtractionCount.incrementAndGet()
        }
    }

    fun recordError(category: ErrorCategory) {
        errorCounts[category]?.incrementAndGet()
    }

    fun recordJsonRepair() {
        repairedJsonCount.incrementAndGet()
    }

    fun recordFuzzyMatch() {
        fuzzyMatchCount.incrementAndGet()
    }

    fun wordsPerMinute(): Double {
        val elapsed = elapsedSeconds()
        if (elapsed < 1.0) return 0.0
        return totalScored.get() * 60.0 / elapsed
    }

    fun successRate(): Double {
        val batches = totalBatches.get()
        if (batches == 0) return 1.0
        return successfulBatches.get().toDouble() / batches
    }

    fun eta(remainingWords: Int): Duration {
        val wpm = wordsPerMinute()
        if (wpm < 0.1) return Duration.ZERO
        val minutes = remainingWords / wpm
        return Duration.ofSeconds((minutes * 60).toLong())
    }

    fun formatProgress(remaining: Int, effectiveBatchSize: Int): String {
        val scored = totalScored.get()
        val failed = totalFailed.get()
        val wpm = wordsPerMinute()
        val etaDuration = eta(remaining)
        val rate = successRate()

        return "scored=$scored failed=$failed remaining=$remaining " +
            "wpm=${"%.1f".format(wpm)} eta=${formatDuration(etaDuration)} " +
            "batch_size=$effectiveBatchSize success_rate=${"%.0f".format(rate * 100)}%"
    }

    fun formatSummary(): String {
        val elapsed = Duration.ofSeconds(elapsedSeconds().toLong())
        val scored = totalScored.get()
        val failed = totalFailed.get()
        val batches = totalBatches.get()

        val lines = buildList {
            add("--- Step 2 Run Summary ---")
            add("Duration: ${formatDuration(elapsed)}")
            add("Words scored: $scored, failed: $failed")
            add("Batches: $batches (success_rate=${"%.0f".format(successRate() * 100)}%)")
            add("Throughput: ${"%.1f".format(wordsPerMinute())} words/min")
            add("JSON repairs: ${repairedJsonCount.get()}")
            add("Fuzzy matches: ${fuzzyMatchCount.get()}")
            add("Partial extractions: ${partialExtractionCount.get()}")

            val errorLines = errorCounts.entries
                .filter { it.value.get() > 0 }
                .sortedByDescending { it.value.get() }
                .joinToString(", ") { "${it.key.name}=${it.value.get()}" }
            if (errorLines.isNotEmpty()) {
                add("Errors: $errorLines")
            }
        }

        return lines.joinToString("\n")
    }

    private fun elapsedSeconds(): Double {
        return Duration.between(startInstant, Instant.now()).toMillis() / 1000.0
    }

    companion object {
        fun categorizeError(message: String?): ErrorCategory {
            if (message == null) return ErrorCategory.OTHER
            val lower = message.lowercase()
            return when {
                lower.contains("missing") && lower.contains("content") -> ErrorCategory.MISSING_CONTENT
                lower.contains("truncat") || lower.contains("unclosed") ||
                    lower.contains("unexpected end") || lower.contains("premature") -> ErrorCategory.TRUNCATED_JSON
                lower.contains("decimal") || lower.contains("number format") -> ErrorCategory.DECIMAL_FORMAT
                lower.contains("mismatch") && lower.contains("word") -> ErrorCategory.WORD_MISMATCH
                lower.contains("model") && (lower.contains("crash") || lower.contains("exit code")) -> ErrorCategory.MODEL_CRASH
                lower.contains("timed out") || lower.contains("connection refused") ||
                    (lower.contains("connect") && lower.contains("fail")) -> ErrorCategory.CONNECTIVITY
                else -> ErrorCategory.OTHER
            }
        }

        internal fun formatDuration(d: Duration): String {
            val totalSeconds = d.seconds
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 -> "${hours}h${minutes}m${seconds}s"
                minutes > 0 -> "${minutes}m${seconds}s"
                else -> "${seconds}s"
            }
        }
    }
}
