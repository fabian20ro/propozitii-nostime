package scrabble.phrases.words

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VerbTest {

    @Test
    fun shouldComputeSyllablesAndRhyme() {
        val verb = Verb("alearg\u0103")
        assertEquals(3, verb.syllables)
        assertEquals("rg\u0103", verb.rhyme)
    }

    @Test
    fun shouldProvideDataClassAccessors() {
        val verb = Verb("merge")
        assertEquals("merge", verb.word)
        assertEquals(2, verb.syllables)
        assertEquals("rge", verb.rhyme)
    }

    @Test
    fun `rejects blank word`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { Verb("  ") }
        assertEquals("verb word must not be blank", ex.message)
    }

    @Test
    fun `rejects single-character word`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { Verb("a") }
        assertEquals("verb word must have at least 2 characters", ex.message)
    }

    @Test
    fun `rejects all-digits word`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { Verb("12345") }
        assertEquals("verb word must contain at least one letter", ex.message)
    }
}
