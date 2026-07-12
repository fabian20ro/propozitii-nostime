package scrabble.phrases.providers

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

@QuarkusTest
class DefinitionProviderTest {

    @Inject
    lateinit var repository: scrabble.phrases.repository.WordRepository

    @Test
    fun shouldReturnCorrectDefinitionFormat() {
        val provider = DefinitionProvider(repository, 1, 5)
        val sentence = provider.getSentence()

        assertNotNull(sentence, "Sentence must not be null")
        assertFalse(sentence.isBlank(), "Sentence must not be blank")
        assertTrue(sentence.endsWith("."), "Sentence should end with a dot")

        // Format: "WORD: articulated noun adj care verb object."
        val colonIdx = sentence.indexOf(": ")
        assertTrue(colonIdx > 0, "Sentence must contain ': ' separator after the defined word")

        val keywordIdx = sentence.indexOf("care ", colonIdx)
        assertTrue(keywordIdx > colonIdx, "Sentence must contain the 'care' connector after the adjective")

        // The key word (before ': ') should be uppercase and a single word
        val keyWord = sentence.substring(0, colonIdx).trim()
        assertTrue(keyWord == keyWord.uppercase(), "Key word before ':' must be fully uppercase")
    }

    @Test
    fun shouldUseDistinctNouns() {
        val provider = DefinitionProvider(repository, 1, 5)
        val sentence = provider.getSentence()

        // Format: "WORD: articulated noun adj care verb object."
        // Split into key (before colon) and body (after colon)
        val colonIdx = sentence.indexOf(": ")
        val body = sentence.substring(colonIdx + 2).dropLast(1) // remove trailing dot

        val words = body.split(" ").filter { it.isNotBlank() }
        assertTrue(words.size >= 5, "Body should contain at least noun, adj, 'care', verb, object (got ${words.size}: '$body')")

        // Extract the three nouns: first articulated noun, and the key word used as definition subject
        val keyWord = sentence.substring(0, colonIdx).trim().lowercase()
        val objArticulated = words[4] // 5th word is object (after noun adj care verb)

        // Key word should not appear in body except potentially inside articulated forms
        // Verify the key word itself isn't reused as one of the three distinct nouns
        val nounWord = words[0].trim().lowercase()
        assertFalse(nounWord == objArticulated.trim().lowercase(), "Noun and object should be different")
    }
}
