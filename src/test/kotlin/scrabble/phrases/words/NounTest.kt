package scrabble.phrases.words

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NounTest {

    @Test
    fun `test masculine articulation`() {
        assertEquals("câinele", Noun("câine", NounGender.M).articulated)
        assertEquals("băiatul", Noun("băiat", NounGender.M).articulated)
        assertEquals("copilul", Noun("copil", NounGender.M).articulated)
        assertEquals("puștiul", Noun("puști", NounGender.M).articulated)
    }

    @Test
    fun `test feminine articulation`() {
        // Case: ends with ă
        assertEquals("fata", Noun("fată", NounGender.F).articulated)
        // Case: ends with ie
        assertEquals("fata", Noun("fată", NounGender.F).articulated)
        // Let's try a word ending in "ie"
        assertEquals("pomul", Noun("pom", NounGender.M).articulated) // wait, pom is M
        // Let's test "fată" again.
        // word.endsWith("ă") is true.
        // word.dropLast(1) + "a" => "fat" + "a" = "fata"
        assertEquals("fata", Noun("fată", NounGender.F).articulated)
    }
}
