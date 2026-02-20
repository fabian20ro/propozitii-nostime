package scrabble.phrases.words

import org.assertj.core.api.Assertions.assertThat
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
        assertThat(noun.articulated).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "fat\u0103, F, fata",
        "macara, F, macaraua",
        "ploaie, F, ploaia",
        "rodie, F, rodia",
    )
    fun shouldArticulateFeminine(word: String, gender: String, expected: String) {
        val noun = Noun(word, NounGender.valueOf(gender))
        assertThat(noun.articulated).isEqualTo(expected)
    }

    @Test
    fun shouldComputeSyllablesAndRhyme() {
        val noun = Noun("macara", NounGender.F)
        assertThat(noun.syllables).isEqualTo(3)
        assertThat(noun.rhyme).isEqualTo("ara")
    }

    @Test
    fun shouldProvideDataClassAccessors() {
        val noun = Noun("carte", NounGender.F)
        assertThat(noun.word).isEqualTo("carte")
        assertThat(noun.gender).isEqualTo(NounGender.F)
        assertThat(noun.articulated).isEqualTo("cartea")
    }
}
