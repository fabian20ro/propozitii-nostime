package scrabble.phrases.tools.rarity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonRepairTest {

    // --- fixTrailingDecimalPoints ---

    @Test
    fun fixes_trailing_decimal_in_confidence_field() {
        val input = """{"confidence": 0.}"""
        val expected = """{"confidence": 0.0}"""
        assertEquals(expected, JsonRepair.fixTrailingDecimalPoints(input))
    }

    @Test
    fun fixes_trailing_decimal_before_comma() {
        val input = """{"confidence": 0., "rarity_level": 3}"""
        val expected = """{"confidence": 0.0, "rarity_level": 3}"""
        assertEquals(expected, JsonRepair.fixTrailingDecimalPoints(input))
    }

    @Test
    fun does_not_modify_valid_decimal() {
        val input = """{"confidence": 0.85}"""
        assertEquals(input, JsonRepair.fixTrailingDecimalPoints(input))
    }

    @Test
    fun does_not_modify_decimal_inside_string() {
        val input = """{"note": "value is 0."}"""
        assertEquals(input, JsonRepair.fixTrailingDecimalPoints(input))
    }

    @Test
    fun fixes_trailing_decimal_at_end_of_input() {
        val input = """{"confidence": 1."""
        val expected = """{"confidence": 1.0"""
        assertEquals(expected, JsonRepair.fixTrailingDecimalPoints(input))
    }

    // --- removeLineComments ---

    @Test
    fun removes_line_comment_after_json_value() {
        val input = """{"rarity_level": 3 // common word
}"""
        val expected = """{"rarity_level": 3
}"""
        assertEquals(expected, JsonRepair.removeLineComments(input))
    }

    @Test
    fun does_not_remove_slashes_inside_string() {
        val input = """{"url": "http://example.com"}"""
        assertEquals(input, JsonRepair.removeLineComments(input))
    }

    @Test
    fun removes_multiple_line_comments() {
        val input = """{"a": 1, // first
"b": 2 // second
}"""
        val expected = """{"a": 1,
"b": 2
}"""
        assertEquals(expected, JsonRepair.removeLineComments(input))
    }

    // --- removeTrailingCommas ---

    @Test
    fun removes_trailing_comma_before_closing_bracket() {
        val input = """[1, 2, 3,]"""
        assertEquals("[1, 2, 3]", JsonRepair.removeTrailingCommas(input))
    }

    @Test
    fun removes_trailing_comma_before_closing_brace() {
        val input = """{"a": 1, "b": 2,}"""
        assertEquals("""{"a": 1, "b": 2}""", JsonRepair.removeTrailingCommas(input))
    }

    @Test
    fun removes_trailing_comma_with_whitespace() {
        val input = """{"a": 1,
}"""
        assertEquals("""{"a": 1}""", JsonRepair.removeTrailingCommas(input))
    }

    // --- closeUnclosedStructures ---

    @Test
    fun closes_unclosed_object() {
        val input = """{"results": [{"word": "apa"""
        val expected = """{"results": [{"word": "apa"}]}"""
        assertEquals(expected, JsonRepair.closeUnclosedStructures(input))
    }

    @Test
    fun closes_unclosed_array() {
        val input = """{"results": [{"word": "apa"}"""
        val expected = """{"results": [{"word": "apa"}]}"""
        assertEquals(expected, JsonRepair.closeUnclosedStructures(input))
    }

    @Test
    fun closes_unclosed_string() {
        val input = """{"word": "apa"""
        val expected = """{"word": "apa"}"""
        assertEquals(expected, JsonRepair.closeUnclosedStructures(input))
    }

    @Test
    fun does_not_modify_valid_json() {
        val input = """{"results": [{"word": "apa", "rarity_level": 2}]}"""
        assertEquals(input, JsonRepair.closeUnclosedStructures(input))
    }

    @Test
    fun handles_deeply_nested_truncation() {
        val input = """{"results": [{"word": "test", "type": "N", "rarity_level": 3, "tag": "common", "confidence": 0.9}, {"word": "alt"""
        val expected = """{"results": [{"word": "test", "type": "N", "rarity_level": 3, "tag": "common", "confidence": 0.9}, {"word": "alt"}]}"""
        assertEquals(expected, JsonRepair.closeUnclosedStructures(input))
    }

    @Test
    fun handles_escaped_quotes_in_strings() {
        val input = """{"word": "te\"st"""
        val expected = """{"word": "te\"st"}"""
        assertEquals(expected, JsonRepair.closeUnclosedStructures(input))
    }

    // --- repair (full pipeline) ---

    @Test
    fun full_repair_of_truncated_response_with_trailing_decimal() {
        val input = """{"results": [{"word": "apa", "type": "N", "rarity_level": 2, "tag": "common", "confidence": 0.}]}"""
        val expected = """{"results": [{"word": "apa", "type": "N", "rarity_level": 2, "tag": "common", "confidence": 0.0}]}"""
        assertEquals(expected, JsonRepair.repair(input))
    }

    @Test
    fun full_repair_of_truncated_mid_object() {
        val input = """{"results": [{"word": "apa", "type": "N", "rarity_level": 2, "tag": "common", "confidence": 0."""
        val expected = """{"results": [{"word": "apa", "type": "N", "rarity_level": 2, "tag": "common", "confidence": 0.0}]}"""
        assertEquals(expected, JsonRepair.repair(input))
    }

    @Test
    fun full_repair_with_trailing_comma_and_truncation() {
        val input = """{"results": [{"word": "apa", "rarity_level": 2,"""
        val expected = """{"results": [{"word": "apa", "rarity_level": 2}]}"""
        assertEquals(expected, JsonRepair.repair(input))
    }

    @Test
    fun full_repair_with_comment_and_truncation() {
        val input = """{"results": [ // word list
{"word": "apa"""
        val expected = """{"results": [
{"word": "apa"}]}"""
        assertEquals(expected, JsonRepair.repair(input))
    }

    @Test
    fun valid_json_passes_through_unchanged() {
        val input = """{"results": [{"word": "apa", "type": "N", "rarity_level": 2, "tag": "common", "confidence": 0.85}]}"""
        assertEquals(input, JsonRepair.repair(input))
    }

    @Test
    fun empty_string_passes_through() {
        assertEquals("", JsonRepair.repair(""))
    }
}
