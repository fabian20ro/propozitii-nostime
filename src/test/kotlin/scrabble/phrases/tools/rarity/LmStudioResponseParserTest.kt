package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import scrabble.phrases.tools.rarity.lmstudio.LmStudioResponseParser

class LmStudioResponseParserTest {

    private val mapper = ObjectMapper()
    private val parser = LmStudioResponseParser(mapper)

    private fun chatResponseRawJson(contentJson: String): String {
        val node = mapper.createObjectNode()
        val choices = mapper.createArrayNode()
        val choice = mapper.createObjectNode()
        val message = mapper.createObjectNode()
        message.put("content", contentJson)
        choice.set<com.fasterxml.jackson.databind.JsonNode>("message", message)
        choices.add(choice)
        node.set<com.fasterxml.jackson.databind.JsonNode>("choices", choices)
        return mapper.writeValueAsString(node)
    }

    private fun batch(vararg words: Triple<Int, String, String>): List<BaseWordRow> {
        return words.map { (id, word, type) -> BaseWordRow(id, word, type) }
    }

    @Test
    fun parses_valid_results_object() {
        val content = """{"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":0.9}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(1, result.scores.size)
        assertEquals(0, result.unresolved.size)
        assertEquals(2, result.scores.first().rarityLevel)
        assertEquals(0.9, result.scores.first().confidence)
    }

    @Test
    fun parses_top_level_array() {
        val content = """[{"word_id":1,"word":"apa","type":"N","rarity_level":3,"tag":"rare","confidence":0.7}]"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(1, result.scores.size)
        assertEquals(3, result.scores.first().rarityLevel)
    }

    @Test
    fun strips_code_fences() {
        val content = "```json\n" +
            """{"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":4,"tag":"rare","confidence":0.6}]}""" +
            "\n```"
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(1, result.scores.size)
        assertEquals(4, result.scores.first().rarityLevel)
    }

    @Test
    fun normalizes_confidence_from_percentage_scale() {
        val content = """{"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":85}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(0.85, result.scores.first().confidence)
    }

    @Test
    fun normalizes_missing_confidence_to_default() {
        val content = """{"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common"}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(0.5, result.scores.first().confidence)
    }

    @Test
    fun parses_word_id_as_string() {
        val content = """{"results":[{"word_id":"1","word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":0.9}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(1, result.scores.size)
        assertEquals(1, result.scores.first().wordId)
    }

    @Test
    fun matches_by_fuzzy_diacritics() {
        val content = """{"results":[{"word_id":1,"word":"stiintific","type":"A","rarity_level":3,"tag":"less_common","confidence":0.7}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "științific", "A")), response)

        assertEquals(1, result.scores.size)
        assertEquals(1, result.scores.first().wordId)
    }

    @Test
    fun salvages_valid_objects_from_partially_malformed_results() {
        val content = """{
          "results": [
            {"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"common","confidence":0.9},
            {"word_id":2,"word":"brad","type":"N","rarity_level":4,"tag":"rare","confidence" 0.8}
          ]
        }"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(1, "apa", "N"), Triple(2, "brad", "N")),
            response
        )

        assertEquals(1, result.scores.size)
        assertEquals(1, result.unresolved.size)
        assertEquals(1, result.scores.first().wordId)
        assertEquals(2, result.unresolved.first().wordId)
    }

    @Test
    fun empty_batch_returns_empty_result() {
        val content = """{"results":[]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(emptyList(), response)

        assertEquals(0, result.scores.size)
        assertEquals(0, result.unresolved.size)
    }

    @Test
    fun extracts_json_after_reasoning_prefix() {
        val content = "Let me analyze each word.\n" +
            """{"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":1,"tag":"common","confidence":0.95}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertEquals(1, result.scores.size)
        assertEquals(1, result.scores.first().rarityLevel)
    }

    @Test
    fun throws_when_no_valid_results_parsed() {
        val content = """{"results":[{"invalid":"data"}]}"""
        val response = chatResponseRawJson(content)

        assertThrows<IllegalStateException> {
            parser.parse(batch(Triple(1, "apa", "N")), response)
        }
    }

    @Test
    fun truncates_tag_to_16_characters() {
        val content = """{"results":[{"word_id":1,"word":"apa","type":"N","rarity_level":2,"tag":"very_long_tag_that_exceeds_sixteen","confidence":0.9}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(batch(Triple(1, "apa", "N")), response)

        assertTrue(result.scores.first().tag.length <= 16)
    }

    @Test
    fun selection_mode_parses_top_level_array_of_ints() {
        val content = """[1,2]"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(1, "apa", "N"), Triple(2, "brad", "N")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 2,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(0, result.unresolved.size)
        assertEquals(listOf(1, 2), result.scores.map { it.wordId }.sorted())
        assertTrue(result.scores.all { it.rarityLevel == 2 })
    }

    @Test
    fun selection_mode_parses_results_array_of_objects_with_local_id_and_word() {
        val content = """{"results":[{"local_id":1,"word":"apa"},{"local_id":"2","word":"brad"}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(1, "apa", "N"), Triple(2, "brad", "N")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 3,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(listOf(1, 2), result.scores.map { it.wordId }.sorted())
        assertTrue(result.scores.all { it.rarityLevel == 3 })
    }

    @Test
    fun selection_mode_falls_back_to_word_match_when_word_id_is_invalid() {
        val content = """{"results":[{"local_id":0,"word":"apa"},{"local_id":999999,"word":"brad"}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(1, "apa", "N"), Triple(2, "brad", "N")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 2,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(listOf(1, 2), result.scores.map { it.wordId }.sorted())
    }

    @Test
    fun selection_mode_falls_back_to_normalized_word_match_when_word_has_punctuation_noise() {
        val content = """{"results":[{"local_id":0,"word":"frigorific?"},{"local_id":0,"word":"depeșat..."}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(10, "frigorific", "A"), Triple(11, "depeșat", "A")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 2,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(listOf(10, 11), result.scores.map { it.wordId }.sorted())
    }

    @Test
    fun selection_mode_keeps_backward_compatibility_for_word_id_field() {
        val content = """{"results":[{"word_id":1,"word":"apa"},{"word_id":2,"word":"brad"}]}"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(1, "apa", "N"), Triple(2, "brad", "N")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 2,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(listOf(1, 2), result.scores.map { it.wordId }.sorted())
    }

    @Test
    fun selection_mode_requires_exact_count() {
        val content = """[1]"""
        val response = chatResponseRawJson(content)

        assertThrows<IllegalStateException> {
            parser.parse(
                batch(Triple(1, "apa", "N"), Triple(2, "brad", "N")),
                response,
                outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
                forcedRarityLevel = 2,
                expectedItems = 2
            )
        }
    }

    @Test
    fun selection_mode_accepts_index_lists_as_fallback() {
        val content = """[0,2]"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(121026, "monarhic", "A"), Triple(120934, "molecular", "A"), Triple(110947, "instrui", "V")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 1,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(setOf(121026, 110947), result.scores.map { it.wordId }.toSet())
    }

    @Test
    fun selection_mode_accepts_one_based_index_lists_as_fallback() {
        val content = """[1,3]"""
        val response = chatResponseRawJson(content)
        val result = parser.parse(
            batch(Triple(121026, "monarhic", "A"), Triple(120934, "molecular", "A"), Triple(110947, "instrui", "V")),
            response,
            outputMode = ScoringOutputMode.SELECTED_WORD_IDS,
            forcedRarityLevel = 1,
            expectedItems = 2
        )

        assertEquals(2, result.scores.size)
        assertEquals(setOf(121026, 110947), result.scores.map { it.wordId }.toSet())
    }
}
