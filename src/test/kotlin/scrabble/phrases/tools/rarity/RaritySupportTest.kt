package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RaritySupportTest {

    @Test
    fun median_odd_length_returns_middle() {
        assertEquals(3, median(listOf(1, 3, 5)))
        assertEquals(2, median(listOf(2)))
    }

    @Test
    fun median_even_length_rounds_half_up() {
        assertEquals(3, median(listOf(2, 3)))
        assertEquals(5, median(listOf(4, 5)))
        assertEquals(2, median(listOf(1, 2)))
        assertEquals(4, median(listOf(3, 4)))
    }

    @Test
    fun median_even_length_exact_average() {
        assertEquals(3, median(listOf(2, 4)))
        assertEquals(2, median(listOf(1, 3)))
    }

    @Test
    fun median_unsorted_input_is_sorted() {
        assertEquals(3, median(listOf(5, 1, 3)))
        assertEquals(3, median(listOf(3, 2)))
    }
}
