package scrabble.phrases.words

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VerbTest {

    @Test
    fun shouldComputeSyllablesAndRhyme() {
        val verb = Verb("alearg\u0103")
        assertThat(verb.syllables).isEqualTo(3)
        assertThat(verb.rhyme).isEqualTo("rg\u0103")
    }

    @Test
    fun shouldProvideDataClassAccessors() {
        val verb = Verb("merge")
        assertThat(verb.word).isEqualTo("merge")
        assertThat(verb.syllables).isEqualTo(2)
        assertThat(verb.rhyme).isEqualTo("rge")
    }
}
