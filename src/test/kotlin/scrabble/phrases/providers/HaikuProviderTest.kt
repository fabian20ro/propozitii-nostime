package scrabble.phrases.providers

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull

@QuarkusTest
class HaikuProviderTest {

    @Inject
    lateinit var repository: scrabble.phrases.repository.WordRepository

    @Test
    fun shouldReturnCorrectHaikuFormat() {
        // Using maxRarity=5 to minimize IllegalStateException during tests
        val provider = HaikuProvider(repository, 1, 5)
        val sentence = provider.getSentence()
        
        assertNotNull(sentence)
        // Haiku format: "Line 1 / Line 2 / Line 3."
        // So there should be exactly 2 " / " delimiters.
        val delimiterCount = sentence.split(" / ").size
        assertTrue(delimiterCount == 3, "Expected 3 parts separated by ' / ', but found $delimiterCount. Sentence: $sentence")
        assertTrue(sentence.endsWith("."), "Sentence should end with a dot")
    }
}
