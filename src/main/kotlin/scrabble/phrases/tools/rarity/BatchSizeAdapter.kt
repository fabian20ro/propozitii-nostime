package scrabble.phrases.tools.rarity

/**
 * Adapts batch size based on a sliding window of recent batch outcomes.
 *
 * When the LM frequently fails on large batches (truncated JSON, malformed output),
 * the adapter shrinks the batch size. When success rates are consistently high,
 * it grows back toward the initial size.
 *
 * Thresholds:
 * - Success rate < 50%: shrink by 1/3 (floor at [minSize])
 * - Success rate > 90%: grow by 50% (cap at [initialSize])
 * - Otherwise: hold steady
 */
class BatchSizeAdapter(
    private val initialSize: Int,
    private val minSize: Int = 3,
    private val windowSize: Int = 10
) {
    init {
        require(initialSize >= minSize) { "initialSize ($initialSize) must be >= minSize ($minSize)" }
        require(minSize >= 1) { "minSize must be >= 1" }
        require(windowSize >= 1) { "windowSize must be >= 1" }
    }

    private val outcomes = ArrayDeque<Boolean>()
    private var currentSize: Int = initialSize

    fun recommendedSize(): Int = currentSize

    fun recordOutcome(success: Boolean) {
        recordOutcome(if (success) 1.0 else 0.0)
    }

    fun recordOutcome(successRatio: Double) {
        val normalized = successRatio.coerceIn(0.0, 1.0)
        val success = normalized >= 0.9
        outcomes.addLast(success)
        if (outcomes.size > windowSize) {
            outcomes.removeFirst()
        }
        adjustSize()
    }

    fun successRate(): Double {
        if (outcomes.isEmpty()) return 1.0
        return outcomes.count { it }.toDouble() / outcomes.size
    }

    private fun adjustSize() {
        val rate = successRate()
        currentSize = when {
            rate < 0.5 -> (currentSize * 2 / 3).coerceAtLeast(minSize)
            rate > 0.9 -> (currentSize * 3 / 2).coerceAtMost(initialSize)
            else -> currentSize
        }
    }
}
