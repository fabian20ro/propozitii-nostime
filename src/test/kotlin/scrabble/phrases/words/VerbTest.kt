package scrabble.phrases.words

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
}
