package scrabble.phrases.providers

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

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
}
