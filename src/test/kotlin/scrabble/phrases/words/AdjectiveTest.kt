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
        "sonor, sonor\u0103",
        "muncitor, muncitoare",
        "lucr\u0103tor, lucr\u0103toare",
        "u\u0219or, u\u0219oar\u0103",
        "acri\u0219or, acri\u0219oar\u0103",
        "superior, superioar\u0103",
        "inferior, inferioar\u0103",
        "frumos, frumoas\u0103",
        "zglobiu, zglobie",
        "st\u00e2ngaci, st\u00e2ngace",
        "acru, acr\u0103",
        "u\u0219urel, u\u0219uric\u0103",
        "frumu\u0219el, frumu\u0219ic\u0103",
        "micu\u021Bel, micu\u021Bic\u0103",
        "sub\u021Birel, sub\u021Biric\u0103",
        "fidel, fidel\u0103",
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
