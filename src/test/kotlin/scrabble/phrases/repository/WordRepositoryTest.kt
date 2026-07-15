package scrabble.phrases.repository

import io.agroal.api.AgroalDataSource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
    fun shouldThrowExceptionWhenNoNounsFound() {
        // Defensive precondition: no nouns at rarity level 5 in seed data.
        val countStmt = dataSource.connection.prepareStatement(
            "SELECT COUNT(*) FROM words WHERE type='N' AND rarity_level=5"
        )
        val exists = countStmt.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        check(exists == 0) { "Expected no nouns at max rarity 5; seed data changed" }

        var caught: IllegalStateException? = null
        try {
            repository.getRandomNoun(minRarity = 5, maxRarity = 5)
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertThat(caught).isNotNull()
        assertThat(caught!!.message).contains("between 5 and 5")
    }

    @Test
    fun shouldThrowExceptionWhenMinRarityGreaterThanMaxRarity() {
        var caught: IllegalStateException? = null
        try {
            repository.getRandomNoun(minRarity = 3, maxRarity = 2)
        } catch (e: IllegalStateException) {
            caught = e
        }
        assertThat(caught).isNotNull()
        assertThat(caught!!.message).contains("between 3 and 2")
    }

    private fun rarityOf(word: String, type: String): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT rarity_level FROM words WHERE word=? AND type=? LIMIT 1").use { stmt ->
                stmt.setString(1, word)
                stmt.setString(2, type)
                stmt.executeQuery().use { rs ->
                    check(rs.next()) { "Missing $word/$type in seed data" }
                    return rs.getInt("rarity_level")
                }
            }
        }
    }
}
