package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BatchSizeAdapterTest {

    @Test
    fun starts_at_initial_size() {
        val adapter = BatchSizeAdapter(initialSize = 20)
        assertEquals(20, adapter.recommendedSize())
    }

    @Test
    fun shrinks_after_repeated_failures() {
        val adapter = BatchSizeAdapter(initialSize = 20, windowSize = 10)
        repeat(6) { adapter.recordOutcome(false) }
        repeat(4) { adapter.recordOutcome(true) }

        assertTrue(adapter.recommendedSize() < 20)
    }

    @Test
    fun does_not_shrink_below_min_size() {
        val adapter = BatchSizeAdapter(initialSize = 6, minSize = 3, windowSize = 5)
        repeat(5) { adapter.recordOutcome(false) }

        assertEquals(3, adapter.recommendedSize())
    }

    @Test
    fun grows_back_after_sustained_success() {
        val adapter = BatchSizeAdapter(initialSize = 20, windowSize = 10)

        repeat(10) { adapter.recordOutcome(false) }
        val shrunkSize = adapter.recommendedSize()
        assertTrue(shrunkSize < 20)

        repeat(10) { adapter.recordOutcome(true) }
        assertTrue(adapter.recommendedSize() > shrunkSize)
    }

    @Test
    fun does_not_grow_beyond_initial_size() {
        val adapter = BatchSizeAdapter(initialSize = 10, windowSize = 5)
        repeat(5) { adapter.recordOutcome(true) }

        assertEquals(10, adapter.recommendedSize())
    }

    @Test
    fun holds_steady_with_mixed_results() {
        val adapter = BatchSizeAdapter(initialSize = 20, windowSize = 10)
        repeat(7) { adapter.recordOutcome(true) }
        repeat(3) { adapter.recordOutcome(false) }

        assertEquals(20, adapter.recommendedSize())
    }

    @Test
    fun success_rate_correct_for_empty_window() {
        val adapter = BatchSizeAdapter(initialSize = 10)
        assertEquals(1.0, adapter.successRate())
    }

    @Test
    fun success_rate_tracks_window() {
        val adapter = BatchSizeAdapter(initialSize = 10, windowSize = 4)
        adapter.recordOutcome(true)
        adapter.recordOutcome(false)
        adapter.recordOutcome(true)
        adapter.recordOutcome(true)

        assertEquals(0.75, adapter.successRate())
    }

    @Test
    fun success_ratio_below_threshold_is_treated_as_failure() {
        val adapter = BatchSizeAdapter(initialSize = 10, minSize = 3, windowSize = 1)
        adapter.recordOutcome(0.89)

        assertEquals(6, adapter.recommendedSize())
    }

    @Test
    fun success_ratio_is_clamped_to_valid_range() {
        val adapter = BatchSizeAdapter(initialSize = 10, minSize = 3, windowSize = 2)
        adapter.recordOutcome(-5.0)
        adapter.recordOutcome(5.0)

        assertEquals(0.5, adapter.successRate())
    }

    @Test
    fun rejects_invalid_initial_size() {
        assertThrows<IllegalArgumentException> {
            BatchSizeAdapter(initialSize = 2, minSize = 5)
        }
    }

    @Test
    fun rejects_invalid_min_size() {
        assertThrows<IllegalArgumentException> {
            BatchSizeAdapter(initialSize = 10, minSize = 0)
        }
    }
}
