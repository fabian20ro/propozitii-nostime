package scrabble.phrases.words

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WordUtilsTest {

    @Test
    fun shouldComputeCorrectSyllableCount() {
        val wordMap = mapOf(
            "semifinalista" to 6, "greoi" to 2, "aalenian" to 4, "alee" to 3, "alcool" to 3,
            "fiinta" to 3, "puicuta" to 3, "aeroport" to 4, "miercureana" to 4, "policioara" to 4,
            "vulpoaica" to 3, "miau" to 1, "leoaica" to 3, "lupoaica" to 3, "mioara" to 2,
            "ambiguul" to 4, "t\u0103m\u00e2ie" to 3, "bou" to 1, "reusit" to 3, "greul" to 2,
            "plouat" to 2, "roua" to 2, "calea" to 2, "eu" to 1, "greu" to 1, "pui" to 1,
            "tuiul" to 2, "ghioc" to 2,
            "laur" to 2, "taur" to 2, "dinozaur" to 4
        )

        for ((word, expected) in wordMap) {
            assertEquals(expected, WordUtils.computeSyllableNumber(word),
                "Syllable count for word: $word")
        }
    }

    @Test
    fun shouldCapitalizeFirstLetter() {
        assertEquals("Aloha", WordUtils.capitalizeFirstLetter("aloha"))
        assertEquals("Aloha", WordUtils.capitalizeFirstLetter("Aloha"))
        assertEquals("\u00cen\u0103bu\u0219eala", WordUtils.capitalizeFirstLetter("\u00een\u0103bu\u0219eala"))
    }

    @Test
    fun shouldComputeRhyme() {
        assertEquals("are", WordUtils.computeRhyme("formare"))
        assertEquals("abc", WordUtils.computeRhyme("abc"))
        assertEquals("ab", WordUtils.computeRhyme("ab"))
    }
}
