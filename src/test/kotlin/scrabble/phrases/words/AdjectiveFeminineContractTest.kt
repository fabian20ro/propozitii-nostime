package scrabble.phrases.words

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AdjectiveFeminineContractTest {

    @Test
    fun checkFeminineTransitions() {
        val cases = mapOf(
            "negru" to "neagră",
            "roșu" to "roșie",
            "sec" to "seacă",
            "des" to "deasă",
            "drept" to "dreaptă",
            "întreg" to "întreagă",
            "deșert" to "deșeartă",
            "mort" to "moartă",
            "calos" to "caloasă",
            "muncitor" to "muncitoare",
            "superior" to "superioară",
            "inferior" to "inferioară",
            "frumos" to "frumoasă",
            "zglobiu" to "zglobie",
            "stângaci" to "stângace",
            "integru" to "integră",
            "acru" to "acră",
            "ușurel" to "ușurică",
            "frumușel" to "frumușică",
            "micuțel" to "micuțică",
            "fidel" to "fidelă",
            "alb" to "albă",
            "verde" to "verde",
            "maro" to "maro",
            "gri" to "gri",
            "roșu" to "roșie"
        )

        cases.forEach { (masculine, expected) ->
            val adj = Adjective(masculine)
            assertThat(adj.feminine).isEqualTo(expected)
        }
    }
}
