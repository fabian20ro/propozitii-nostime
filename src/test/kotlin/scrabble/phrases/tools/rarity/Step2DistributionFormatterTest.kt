package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Step2DistributionFormatterTest {

    @Test
    fun formats_zero_distribution_when_no_scored_rows() {
        val counts = IntArray(6)

        val formatted = formatRarityDistribution(counts)

        assertEquals(
            "distribution=[1:0(0.0%) 2:0(0.0%) 3:0(0.0%) 4:0(0.0%) 5:0(0.0%)]",
            formatted
        )
    }

    @Test
    fun formats_distribution_with_percentages() {
        val counts = IntArray(6)
        counts[1] = 1
        counts[2] = 2
        counts[3] = 3
        counts[4] = 4
        counts[5] = 10

        val formatted = formatRarityDistribution(counts)

        assertEquals(
            "distribution=[1:1(5.0%) 2:2(10.0%) 3:3(15.0%) 4:4(20.0%) 5:10(50.0%)]",
            formatted
        )
    }
}
