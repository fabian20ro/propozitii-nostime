package scrabble.phrases.words

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NounTest {

    @ParameterizedTest
    @CsvSource(
        "acar, M, acarul",
        "maestru, M, maestrul",
        "codru, M, codrul",
        "staul, N, staulul",
        "pod, N, podul",
        "munte, M, muntele",
        "perete, M, peretele",
        "burete, M, buretele",
        "tată, M, tatăl",
    )
    fun shouldArticulateMasculineAndNeutral(word: String, gender: String, expected: String) {
        val noun = Noun(word, NounGender.valueOf(gender))
        assertEquals(expected, noun.articulated)
    }

    @ParameterizedTest
    @CsvSource(
        "fată, F, fata",
        "macara, F, macaraua",
        "ploaie, F, ploaia",
        "rodie, F, rodia",
        // Masculine row — proves M-gender routing diverges from feminine;
        // masculine path appends 'ul' → macaraul (vs macaraua under F).
        "macara, M, macaraul",
    )
    fun shouldArticulateFeminine(word: String, gender: String, expected: String) {
        val noun = Noun(word, NounGender.valueOf(gender))
        assertEquals(expected, noun.articulated)
    }

    @Test
    fun shouldComputeSyllablesAndRhyme() {
        val noun = Noun("macara", NounGender.F)
        assertEquals(3, noun.syllables)
        assertEquals("ara", noun.rhyme)
    }
}
