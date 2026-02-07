package scrabble.phrases.repository

import io.agroal.api.AgroalDataSource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class WordRepositoryTest {

    @Inject
    lateinit var repository: WordRepository

    @Inject
    lateinit var dataSource: AgroalDataSource

    @Test
    fun shouldApplyRarityFilterForNouns() {
        repeat(10) {
            val noun = repository.getRandomNoun(maxRarity = 1)
            assertThat(rarityOf(noun.word, "N")).isLessThanOrEqualTo(1)
        }
    }

    @Test
    fun shouldApplyRarityFilterWhenExcludeIsUsed() {
        val noun1 = repository.getRandomNoun(maxRarity = 1)
        val noun2 = repository.getRandomNoun(maxRarity = 1, exclude = setOf(noun1.word))

        assertThat(noun2.word).isNotEqualTo(noun1.word)
        assertThat(rarityOf(noun2.word, "N")).isLessThanOrEqualTo(1)
    }

    @Test
    fun shouldFilterRhymeGroupsByRarityThreshold() {
        assertThat(repository.findTwoRhymeGroups("N", 2, maxRarity = 1)).isNull()
        assertThat(repository.findTwoRhymeGroups("N", 2, maxRarity = 2)).isNotNull()
    }

    @Test
    fun shouldFilterPrefixesByRarityThreshold() {
        assertThat(repository.getRandomPrefixWithAllTypes(maxRarity = 1)).isNull()
        assertThat(repository.getRandomPrefixWithAllTypes(maxRarity = 2)).isNotNull()
    }

    @Test
    fun shouldFilterRhymeCandidatesByRarity() {
        assertThat(repository.getRandomVerbByRhyme("ază", maxRarity = 1)).isNull()
        assertThat(repository.getRandomVerbByRhyme("ază", maxRarity = 2)).isNotNull()
    }

    private fun rarityOf(word: String, type: String): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT rarity_level FROM words WHERE word=? AND type=? LIMIT 1").use { stmt ->
                stmt.setString(1, word)
                stmt.setString(2, type)
                stmt.executeQuery().use { rs ->
                    check(rs.next()) { "Missing word in seed data: $word ($type)" }
                    return rs.getInt("rarity_level")
                }
            }
        }
    }
}
