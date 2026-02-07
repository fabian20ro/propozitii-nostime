package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class Step2MetricsTest {

    @Test
    fun categorizes_truncated_json_error() {
        assertEquals(
            Step2Metrics.ErrorCategory.TRUNCATED_JSON,
            Step2Metrics.categorizeError("Unexpected end of JSON input")
        )
    }

    @Test
    fun categorizes_missing_content_error() {
        assertEquals(
            Step2Metrics.ErrorCategory.MISSING_CONTENT,
            Step2Metrics.categorizeError("LMStudio response missing assistant content")
        )
    }

    @Test
    fun categorizes_word_mismatch_error() {
        assertEquals(
            Step2Metrics.ErrorCategory.WORD_MISMATCH,
            Step2Metrics.categorizeError("Result order/content mismatch for word_id=123")
        )
    }

    @Test
    fun categorizes_model_crash_error() {
        assertEquals(
            Step2Metrics.ErrorCategory.MODEL_CRASH,
            Step2Metrics.categorizeError("HTTP 400: model has crashed with exit code 1")
        )
    }

    @Test
    fun categorizes_connectivity_error() {
        assertEquals(
            Step2Metrics.ErrorCategory.CONNECTIVITY,
            Step2Metrics.categorizeError("Request timed out after 300s")
        )
    }

    @Test
    fun categorizes_unknown_error_as_other() {
        assertEquals(
            Step2Metrics.ErrorCategory.OTHER,
            Step2Metrics.categorizeError("something completely unexpected")
        )
    }

    @Test
    fun categorizes_null_as_other() {
        assertEquals(
            Step2Metrics.ErrorCategory.OTHER,
            Step2Metrics.categorizeError(null)
        )
    }

    @Test
    fun success_rate_with_no_batches() {
        val metrics = Step2Metrics()
        assertEquals(1.0, metrics.successRate())
    }

    @Test
    fun success_rate_tracks_batches() {
        val metrics = Step2Metrics()
        metrics.recordBatchResult(10, 10) // full success
        metrics.recordBatchResult(10, 0)  // full failure
        assertEquals(0.5, metrics.successRate())
    }

    @Test
    fun records_partial_extraction() {
        val metrics = Step2Metrics()
        metrics.recordBatchResult(10, 8)
        val summary = metrics.formatSummary()
        assertTrue(summary.contains("Partial extractions: 1"))
    }

    @Test
    fun records_json_repair_count() {
        val metrics = Step2Metrics()
        metrics.recordJsonRepair()
        metrics.recordJsonRepair()
        val summary = metrics.formatSummary()
        assertTrue(summary.contains("JSON repairs: 2"))
    }

    @Test
    fun records_fuzzy_match_count() {
        val metrics = Step2Metrics()
        metrics.recordFuzzyMatch()
        val summary = metrics.formatSummary()
        assertTrue(summary.contains("Fuzzy matches: 1"))
    }

    @Test
    fun format_duration_hours() {
        assertEquals("1h30m0s", Step2Metrics.formatDuration(Duration.ofMinutes(90)))
    }

    @Test
    fun format_duration_minutes() {
        assertEquals("5m30s", Step2Metrics.formatDuration(Duration.ofSeconds(330)))
    }

    @Test
    fun format_duration_seconds() {
        assertEquals("45s", Step2Metrics.formatDuration(Duration.ofSeconds(45)))
    }

    @Test
    fun eta_returns_zero_when_no_data() {
        val metrics = Step2Metrics()
        assertEquals(Duration.ZERO, metrics.eta(1000))
    }

    @Test
    fun format_progress_includes_key_fields() {
        val metrics = Step2Metrics()
        metrics.recordBatchResult(10, 10)
        val progress = metrics.formatProgress(remaining = 90, effectiveBatchSize = 10)
        assertTrue(progress.contains("scored=10"))
        assertTrue(progress.contains("remaining=90"))
        assertTrue(progress.contains("batch_size=10"))
        assertTrue(progress.contains("success_rate=100%"))
    }

    @Test
    fun format_summary_includes_error_breakdown() {
        val metrics = Step2Metrics()
        metrics.recordError(Step2Metrics.ErrorCategory.TRUNCATED_JSON)
        metrics.recordError(Step2Metrics.ErrorCategory.TRUNCATED_JSON)
        metrics.recordError(Step2Metrics.ErrorCategory.MODEL_CRASH)
        val summary = metrics.formatSummary()
        assertTrue(summary.contains("TRUNCATED_JSON=2"))
        assertTrue(summary.contains("MODEL_CRASH=1"))
    }
}
