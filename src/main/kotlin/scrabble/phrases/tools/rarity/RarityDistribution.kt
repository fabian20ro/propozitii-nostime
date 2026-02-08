package scrabble.phrases.tools.rarity

import java.util.Locale

class RarityDistribution private constructor(
    private val counts: IntArray
) {

    companion object {
        fun empty(): RarityDistribution = RarityDistribution(IntArray(6))

        fun fromLevels(levels: Collection<Int>): RarityDistribution {
            val distribution = empty()
            levels.forEach { distribution.increment(it) }
            return distribution
        }
    }

    fun increment(level: Int) {
        if (level in 1..5) counts[level] += 1
    }

    fun setLevel(previousLevel: Int?, newLevel: Int) {
        previousLevel?.let { level ->
            if (level in 1..5 && counts[level] > 0) {
                counts[level] -= 1
            }
        }
        if (newLevel in 1..5) counts[newLevel] += 1
    }

    fun format(): String = formatRarityDistribution(counts)
}

fun formatRarityDistribution(rarityCounts: IntArray): String {
    val total = (1..5).sumOf { level -> rarityCounts.getOrElse(level) { 0 } }
    val parts = (1..5).joinToString(" ") { level ->
        val count = rarityCounts.getOrElse(level) { 0 }
        val percent = if (total > 0) (count * 100.0) / total.toDouble() else 0.0
        "$level:$count(${String.format(Locale.ROOT, "%.1f", percent)}%)"
    }
    return "distribution=[$parts]"
}
