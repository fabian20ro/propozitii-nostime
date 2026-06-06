package scrabble.phrases.providers

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
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
    }
}
