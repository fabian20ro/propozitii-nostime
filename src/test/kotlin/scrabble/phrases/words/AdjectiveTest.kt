package scrabble.phrases.words

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AdjectiveTest {

    @ParameterizedTest
    @CsvSource(
        "pitoresc, pitoreasc\u0103",
        "cite\u021B, citea\u021B\u0103",
        "bor, boare",
        "frumos, frumoas\u0103",
        "zglobiu, zglobie",
        "st\u00e2ngaci, st\u00e2ngace",
        "acru, acr\u0103",
        "alb, alb\u0103",
        "verde, verde",
        "maro, maro",
        "gri, gri",
    )
    fun shouldDeriveFeminineForm(masculine: String, expectedFeminine: String) {
        val adj = Adjective(masculine)
        assertThat(adj.feminine).isEqualTo(expectedFeminine)
    }

    @Test
    fun shouldComputeSyllablesAndRhyme() {
        val adj = Adjective("frumos")
        assertThat(adj.syllables).isEqualTo(2)
        assertThat(adj.rhyme).isEqualTo("mos")
    }

    @Test
    fun shouldProvideDataClassAccessors() {
        val adj = Adjective("mare")
        assertThat(adj.word).isEqualTo("mare")
        assertThat(adj.feminine).isEqualTo("mare")
    }
}
