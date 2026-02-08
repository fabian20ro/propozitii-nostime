package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RarityDistributionTest {

    @Test
    fun fromLevels_and_setLevel_keep_distribution_consistent() {
        val distribution = RarityDistribution.fromLevels(listOf(1, 2, 2, 4, 5))
        assertEquals(
            "distribution=[1:1(20.0%) 2:2(40.0%) 3:0(0.0%) 4:1(20.0%) 5:1(20.0%)]",
            distribution.format()
        )

        distribution.setLevel(previousLevel = 4, newLevel = 3)
        assertEquals(
            "distribution=[1:1(20.0%) 2:2(40.0%) 3:1(20.0%) 4:0(0.0%) 5:1(20.0%)]",
            distribution.format()
        )
    }
}
