package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scrabble.phrases.tools.rarity.lmstudio.LmStudioErrorClassifier

class LmStudioErrorClassifierTest {

    @Test
    fun detects_unsupported_response_format_json_object() {
        val e = IllegalStateException("response_format unsupported for this model")
        assertTrue(LmStudioErrorClassifier.isUnsupportedResponseFormat(e))
    }

    @Test
    fun detects_unsupported_response_format_must_be_json_schema() {
        val e = IllegalStateException("'response_format.type' must be 'json_schema' or 'text'")
        assertTrue(LmStudioErrorClassifier.isUnsupportedResponseFormat(e))
    }

    @Test
    fun non_response_format_error_is_not_flagged() {
        val e = IllegalStateException("some other error")
        assertFalse(LmStudioErrorClassifier.isUnsupportedResponseFormat(e))
    }

    @Test
    fun detects_switch_to_json_schema() {
        val e = IllegalStateException("'response_format.type' must be 'json_schema' or 'text'")
        assertTrue(LmStudioErrorClassifier.shouldSwitchToJsonSchema(e))
    }

    @Test
    fun does_not_switch_for_generic_format_error() {
        val e = IllegalStateException("response_format unsupported")
        assertFalse(LmStudioErrorClassifier.shouldSwitchToJsonSchema(e))
    }

    @Test
    fun detects_unsupported_reasoning_effort() {
        val e = IllegalStateException("unknown field 'reasoning_effort'")
        assertTrue(LmStudioErrorClassifier.isUnsupportedReasoningControls(e))
    }

    @Test
    fun detects_unsupported_thinking_type() {
        val e = IllegalStateException("unexpected field 'thinking' in request body")
        assertTrue(LmStudioErrorClassifier.isUnsupportedReasoningControls(e))
    }

    @Test
    fun detects_unsupported_enable_thinking() {
        val e = IllegalStateException("invalid parameter 'chat_template_kwargs'")
        assertTrue(LmStudioErrorClassifier.isUnsupportedReasoningControls(e))
    }

    @Test
    fun non_reasoning_error_is_not_flagged() {
        val e = IllegalStateException("connection refused")
        assertFalse(LmStudioErrorClassifier.isUnsupportedReasoningControls(e))
    }

    @Test
    fun detects_empty_parsed_results() {
        val e = IllegalStateException("No valid results parsed from 0 result nodes for batch of 5")
        assertTrue(LmStudioErrorClassifier.isEmptyParsedResults(e))
    }

    @Test
    fun non_empty_results_not_flagged() {
        val e = IllegalStateException("No valid results parsed from 3 result nodes for batch of 5")
        assertFalse(LmStudioErrorClassifier.isEmptyParsedResults(e))
    }

    @Test
    fun excerpt_truncates_long_content() {
        val long = "a".repeat(1000)
        val result = LmStudioErrorClassifier.excerptForLog(long, maxChars = 100)!!
        assertTrue(result.length < 120)
        assertTrue(result.endsWith("...(truncated)"))
    }

    @Test
    fun excerpt_preserves_short_content() {
        assertEquals("hello world", LmStudioErrorClassifier.excerptForLog("hello world"))
    }

    @Test
    fun excerpt_returns_null_for_blank() {
        assertNull(LmStudioErrorClassifier.excerptForLog(""))
        assertNull(LmStudioErrorClassifier.excerptForLog(null))
    }

    @Test
    fun excerpt_collapses_whitespace() {
        assertEquals("a b c", LmStudioErrorClassifier.excerptForLog("a  \n  b   c"))
    }
}
