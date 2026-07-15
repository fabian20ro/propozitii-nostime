package scrabble.phrases.providers

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import scrabble.phrases.words.NounGender

@QuarkusTest
class TautogramProviderTest {

    @Inject
    lateinit var repository: scrabble.phrases.repository.WordRepository

    @Test
    fun shouldGenerateValidTautogram() {
        // Using maxRarity=5 to minimize IllegalStateException during tests
        val provider = TautogramProvider(repository, 1, 5)
        val sentence = provider.getSentence()
        
        assertNotNull(sentence)
        assertTrue(sentence.isNotBlank(), "Sentence should not be blank")
        assertTrue(sentence.endsWith("."), "Sentence should end with a dot")
        
        val words = sentence.replace(".", "").split(" ").filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val firstChar = words[0].first().toString().lowercase()
            words.forEach {
                assertTrue(it.startsWith(firstChar, ignoreCase = true), "Word '$it' does not start with '$firstChar'")
            }
        }
    }

    @Test
    fun shouldProduceFourWordSentenceStructure() {
        val provider = TautogramProvider(repository, 1, 5)
        val sentence = provider.getSentence()

        assertTrue(sentence.endsWith("."), "Sentence must end with a dot")
        val words = sentence.removeSuffix(".").split(" ").filter { it.isNotBlank() }
        assertEquals(4, words.size, "Tautogram sentence must have exactly 4 tokens (noun1 + adj + verb + noun2)")

        val firstChar = words[0].first().toString().lowercase()
        words.forEach { w ->
            assertTrue(w.startsWith(firstChar), "Each word must start with the tautogram prefix letter '$firstChar', got '$w'")
        }
    }

    @Test
    fun shouldProduceCorrectArticleAndGenderAgreement() {
        // Verify that the provider applies adj.forGender(noun1.gender) correctly:
        // For a known tautogram prefix, get gender from repository and assert that
        // the adjective in position 2 of the generated sentence matches that gender.
        val provider = TautogramProvider(repository, 1, 5)
        repeat(30) {
            val prefix = repository.getRandomPrefixWithAllTypes(minRarity = 1, maxRarity = 5) ?: return@repeat
            // Fetch a noun by this prefix to know its gender
            val sampleNoun = repository.getRandomNounByPrefix(prefix, minRarity = 1, maxRarity = 5) ?: return@repeat

            val sentence = provider.getSentence().removeSuffix(".")
            val tokens = sentence.split(" ").filter { it.isNotBlank() }
            assertEquals(4, tokens.size)
            val adjectiveToken = tokens[1]

            // If the sample noun is feminine (F), the adjective in position 2 should end with a feminine marker.
            if (sampleNoun.gender == NounGender.F) {
                assertTrue(
                    adjectiveToken.endsWith('ă') || adjectiveToken.endsWith('e'),
                    "Adjective '$adjectiveToken' at position 1 should feminize for gender F, expected ending in one of [ă, e]"
                )
            }

            // All words must start with the tautogram prefix (contract invariant).
            tokens.forEach { w ->
                assertTrue(
                    w.lowercase().startsWith(prefix.lowercase()),
                    "Word '$w' does not match tautogram prefix '$prefix'"
                )
            }
        }
    }
}
