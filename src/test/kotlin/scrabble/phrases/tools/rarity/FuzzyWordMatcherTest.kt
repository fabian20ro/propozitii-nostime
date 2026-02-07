package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuzzyWordMatcherTest {

    @Test
    fun exact_match_returns_true() {
        assertTrue(FuzzyWordMatcher.matches("apă", "apă"))
    }

    @Test
    fun matches_diacritical_transposition() {
        // Real case from production: LM swaps ț position
        assertTrue(FuzzyWordMatcher.matches("abreviațiune", "abrevițiune"))
    }

    @Test
    fun matches_comma_below_vs_cedilla() {
        assertTrue(FuzzyWordMatcher.matches("șarpe", "şarpe"))
        assertTrue(FuzzyWordMatcher.matches("țară", "ţară"))
    }

    @Test
    fun matches_breve_variants() {
        assertTrue(FuzzyWordMatcher.matches("băiat", "baiat"))
    }

    @Test
    fun matches_circumflex_variants() {
        assertTrue(FuzzyWordMatcher.matches("România", "Romania"))
        assertTrue(FuzzyWordMatcher.matches("încă", "inca"))
    }

    @Test
    fun rejects_completely_different_words() {
        assertFalse(FuzzyWordMatcher.matches("apa", "brad"))
    }

    @Test
    fun rejects_words_beyond_edit_distance() {
        // edit distance 4 -- well beyond threshold
        assertFalse(FuzzyWordMatcher.matches("masa", "masinii"))
    }

    @Test
    fun accepts_single_char_deletion() {
        // LM drops one character -- within edit distance
        assertTrue(FuzzyWordMatcher.matches("masa", "mas"))
    }

    @Test
    fun levenshtein_correct_for_known_pairs() {
        assertEquals(0, FuzzyWordMatcher.levenshtein("abc", "abc"))
        assertEquals(1, FuzzyWordMatcher.levenshtein("abc", "ab"))
        assertEquals(1, FuzzyWordMatcher.levenshtein("abc", "abcd"))
        assertEquals(1, FuzzyWordMatcher.levenshtein("abc", "abc2"))
        assertEquals(3, FuzzyWordMatcher.levenshtein("abc", "xyz"))
    }

    @Test
    fun normalize_strips_all_diacritics() {
        assertEquals("abreviatiune", FuzzyWordMatcher.normalize("abreviațiune"))
        assertEquals("sarpe", FuzzyWordMatcher.normalize("șarpe"))
        assertEquals("tara", FuzzyWordMatcher.normalize("țară"))
        assertEquals("romania", FuzzyWordMatcher.normalize("România"))
    }

    @Test
    fun normalize_preserves_ascii() {
        assertEquals("hello", FuzzyWordMatcher.normalize("hello"))
    }

    @Test
    fun empty_strings_match() {
        assertTrue(FuzzyWordMatcher.matches("", ""))
    }

    @Test
    fun case_insensitive_after_normalization() {
        assertTrue(FuzzyWordMatcher.matches("Apa", "apa"))
    }
}
