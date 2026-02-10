package scrabble.phrases.tools.rarity.lmstudio

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import scrabble.phrases.tools.rarity.*

data class ParsedBatch(
    val scores: List<ScoreResult>,
    val unresolved: List<BaseWordRow>
)

private data class ScoreCandidate(
    val wordId: Int?,
    val word: String?,
    val type: String?,
    val rarityLevel: Int?,
    val tag: String,
    val confidence: Double
)

class LmStudioResponseParser(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val metrics: Step2Metrics? = null
) {

    fun parse(
        batch: List<BaseWordRow>,
        responseBody: String,
        outputMode: ScoringOutputMode = ScoringOutputMode.SCORE_RESULTS,
        forcedRarityLevel: Int? = null,
        expectedItems: Int? = null
    ): ParsedBatch {
        val root = mapper.readTree(responseBody)
        val content = extractModelContent(root)
            ?: throw IllegalStateException("LMStudio response missing assistant content")

        val repairedContent = JsonRepair.repair(content)
        if (repairedContent != content) {
            metrics?.recordJsonRepair()
        }

        val contentJson = parseContentJson(repairedContent)
        val results = extractResultsArray(contentJson)
        return when (outputMode) {
            ScoringOutputMode.SCORE_RESULTS -> parseResultsLenient(batch, results)
            ScoringOutputMode.SELECTED_WORD_IDS ->
                parseSelectedWordIds(batch, results, forcedRarityLevel = forcedRarityLevel, expectedItems = expectedItems)
        }
    }

    private fun parseSelectedWordIds(
        batch: List<BaseWordRow>,
        results: JsonNode,
        forcedRarityLevel: Int?,
        expectedItems: Int?
    ): ParsedBatch {
        val rarityLevel = forcedRarityLevel?.takeIf { it in 1..5 }
            ?: throw IllegalArgumentException("forcedRarityLevel is required for outputMode=SELECTED_WORD_IDS")
        val expected = expectedItems?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("expectedItems is required for outputMode=SELECTED_WORD_IDS")

        if (batch.isEmpty()) return ParsedBatch(scores = emptyList(), unresolved = emptyList())

        val batchById = batch.associateBy { it.wordId }
        val selectedIds = mutableListOf<Int>()

        for (i in 0 until results.size()) {
            val node = results[i]
            val id = nodeToInt(node) ?: nodeToInt(node.path("word_id")) ?: continue
            if (id in batchById) selectedIds += id
        }

        val distinct = selectedIds.distinct()
        if (distinct.size != expected) {
            throw IllegalStateException(
                "Expected exactly $expected selected ids, got ${distinct.size} for batch of ${batch.size}"
            )
        }

        val scores = distinct.map { id ->
            val row = batchById[id] ?: error("Internal error: selected id $id not in batch")
            ScoreResult(
                wordId = row.wordId,
                word = row.word,
                type = row.type,
                rarityLevel = rarityLevel,
                tag = "common",
                confidence = 0.9
            )
        }

        return ParsedBatch(scores = scores, unresolved = emptyList())
    }

    private fun parseResultsLenient(
        batch: List<BaseWordRow>,
        results: JsonNode
    ): ParsedBatch {
        if (batch.isEmpty()) {
            return ParsedBatch(scores = emptyList(), unresolved = emptyList())
        }

        val pendingByWordId = batch.associateBy { it.wordId }.toMutableMap()
        val pendingByWordType = batch
            .groupBy { it.word to it.type }
            .mapValues { (_, rows) -> rows.toMutableList() }
            .toMutableMap()

        val scored = mutableListOf<ScoreResult>()
        for (i in 0 until results.size()) {
            val candidate = parseScoreCandidate(results[i]) ?: continue
            val matched = matchCandidate(candidate, pendingByWordId, pendingByWordType) ?: continue

            scored += ScoreResult(
                wordId = matched.wordId,
                word = matched.word,
                type = matched.type,
                rarityLevel = checkNotNull(candidate.rarityLevel) { "rarityLevel null after parseScoreCandidate" },
                tag = candidate.tag.ifBlank { "uncertain" }.take(16),
                confidence = candidate.confidence
            )
        }

        val unresolved = pendingByWordId.values.sortedBy { it.wordId }

        if (scored.isEmpty() && unresolved.size == batch.size) {
            throw IllegalStateException(
                "No valid results parsed from ${results.size()} result nodes for batch of ${batch.size}"
            )
        }

        if (unresolved.isNotEmpty()) {
            metrics?.recordError(Step2Metrics.ErrorCategory.WORD_MISMATCH)
        }

        return ParsedBatch(scores = scored, unresolved = unresolved)
    }

    /** Returns null if rarity_level is missing or out of 1..5. When non-null, [ScoreCandidate.rarityLevel] is always set. */
    private fun parseScoreCandidate(node: JsonNode): ScoreCandidate? {
        val rarity = node.path("rarity_level").asInt(-1)
        if (rarity !in 1..5) return null

        val wordId = nodeToInt(node.path("word_id"))

        val word = node.path("word").asText("").ifBlank { null }
        val type = node.path("type").asText("").ifBlank { null }
        val tag = node.path("tag").asText("uncertain")
        val confidence = normalizeConfidence(parseConfidence(node.path("confidence")))

        return ScoreCandidate(
            wordId = wordId,
            word = word,
            type = type,
            rarityLevel = rarity,
            tag = tag,
            confidence = confidence
        )
    }

    private fun matchCandidate(
        candidate: ScoreCandidate,
        pendingByWordId: MutableMap<Int, BaseWordRow>,
        pendingByWordType: MutableMap<Pair<String, String>, MutableList<BaseWordRow>>
    ): BaseWordRow? {
        candidate.wordId?.let { id ->
            val row = pendingByWordId.remove(id) ?: return@let
            val key = row.word to row.type
            pendingByWordType.removeById(key, id)
            return row
        }

        val word = candidate.word ?: return null
        val type = candidate.type ?: return null
        val exactKey = word to type

        val exact = pendingByWordType.takeFirstFrom(exactKey)
        if (exact != null) {
            pendingByWordId.remove(exact.wordId)
            return exact
        }

        val fuzzy = pendingByWordType.takeFirstFuzzy(word, type)
        if (fuzzy != null) {
            pendingByWordId.remove(fuzzy.wordId)
            metrics?.recordFuzzyMatch()
            return fuzzy
        }

        return null
    }

    private fun parseContentJson(content: String): JsonNode {
        val excerpt = LmStudioErrorClassifier.excerptForLog(content)

        // 1. Try parsing the full content directly
        val direct = runCatching { mapper.readTree(content) }.getOrNull()
        if (direct != null && !direct.isValueNode) return direct

        // 2. Extract the first JSON block (content may have surrounding text)
        val extracted = extractFirstJsonBlock(content)
            ?: throw IllegalStateException(
                if (direct != null) "LMStudio content is not a JSON object/array. Excerpt: $excerpt"
                else "LMStudio content is not valid JSON. Excerpt: $excerpt"
            )

        // 3. Try parsing the extracted block
        val extractedParse = runCatching { mapper.readTree(extracted) }
        val extractedJson = extractedParse.getOrNull()
        if (extractedJson != null && !extractedJson.isValueNode) return extractedJson

        // 4. Try salvaging individual objects from malformed content
        salvageResultsFromMalformedContent(extracted)?.let { return it }

        // 5. Report failure with the most specific error available
        val reason = if (extractedJson != null) {
            "not a JSON object/array"
        } else {
            extractedParse.exceptionOrNull()?.message ?: "unknown parse error"
        }
        throw IllegalStateException("LMStudio content JSON parse failed: $reason. Excerpt: $excerpt")
    }

    private fun extractResultsArray(contentJson: JsonNode): JsonNode {
        if (contentJson.isArray) return contentJson

        if (contentJson.isObject) {
            val keysByPriority = listOf("results", "items", "data", "predictions")
            for (key in keysByPriority) {
                val node = contentJson.path(key)
                if (node.isArray) return node
            }

            val arrayField = contentJson.fields().asSequence().firstOrNull { it.value.isArray }
            if (arrayField != null) return arrayField.value

            val keys = contentJson.fieldNames().asSequence().toList().joinToString(",")
            throw IllegalStateException("LMStudio content has no results array. Object keys: [$keys]")
        }

        throw IllegalStateException("LMStudio content must be JSON object/array, got: ${contentJson.nodeType}")
    }

    private fun extractModelContent(root: JsonNode): String? {
        return nodeToContentText(root.path("choices").path(0).path("message").path("content"))
            ?: nodeToContentText(root.path("message").path("content"))
            ?: nodeToContentText(root.path("output_text"))
    }

    private fun nodeToContentText(node: JsonNode?): String? {
        if (node == null || node.isMissingNode || node.isNull) return null

        val raw = when {
            node.isTextual -> node.asText()
            node.isArray -> node.mapNotNull { part ->
                when {
                    part.isTextual -> part.asText()
                    part.isObject -> part.path("text").asText(null)
                    else -> null
                }
            }.joinToString("")
            node.isObject -> mapper.writeValueAsString(node)
            else -> node.toString()
        }

        return stripCodeFences(raw).ifBlank { null }
    }

    private fun stripCodeFences(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractFirstJsonBlock(content: String): String? {
        val start = content.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null

        var objectDepth = 0
        var arrayDepth = 0
        var endIndex = -1

        walkJsonChars(content, startIndex = start) { i, ch, inString ->
            if (!inString) {
                when (ch) {
                    '{' -> objectDepth++
                    '}' -> objectDepth--
                    '[' -> arrayDepth++
                    ']' -> arrayDepth--
                }
                if (objectDepth == 0 && arrayDepth == 0) {
                    endIndex = i
                    return@walkJsonChars content.length // stop walking
                }
            }
            i + 1
        }

        if (endIndex < 0) return null
        return content.substring(start, endIndex + 1).trim()
    }

    /**
     * Some local models emit a mostly-valid `results` payload where one object is malformed
     * (e.g. missing `:`) and breaks full JSON parsing. Recover parsable top-level objects so
     * only unresolved words are retried instead of collapsing the full batch.
     */
    private fun salvageResultsFromMalformedContent(content: String): JsonNode? {
        val arraySlice = extractLikelyResultsArraySlice(content) ?: return null
        val objectSlices = extractTopLevelObjectSlices(arraySlice)
        if (objectSlices.isEmpty()) return null

        val results = mapper.createArrayNode()
        for (slice in objectSlices) {
            val repaired = JsonRepair.repair(slice)
            val node = runCatching { mapper.readTree(repaired) }.getOrNull() ?: continue
            if (node.isObject) {
                results.add(node)
            }
        }

        if (results.size() == 0) return null

        metrics?.recordJsonRepair()
        val wrapper = mapper.createObjectNode()
        wrapper.set<JsonNode>("results", results)
        return wrapper
    }

    private fun extractLikelyResultsArraySlice(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.startsWith("[")) return trimmed

        val resultsKeyIndex = content.indexOf("\"results\"")
        val searchStart = if (resultsKeyIndex >= 0) resultsKeyIndex else 0
        val arrayStart = content.indexOf('[', startIndex = searchStart)
        if (arrayStart < 0) return null

        val arrayEnd = findMatchingClosingIndex(content, arrayStart, '[', ']')
        if (arrayEnd < 0) return null
        return content.substring(arrayStart, arrayEnd + 1)
    }

    private fun findMatchingClosingIndex(
        text: String,
        start: Int,
        opener: Char,
        closer: Char
    ): Int {
        if (start !in text.indices || text[start] != opener) return -1
        var depth = 0
        var result = -1

        walkJsonChars(text, startIndex = start) { i, ch, inString ->
            if (!inString) {
                if (ch == opener) depth++
                if (ch == closer) depth--
                if (depth == 0) {
                    result = i
                    return@walkJsonChars text.length // stop walking
                }
            }
            i + 1
        }

        return result
    }

    private fun extractTopLevelObjectSlices(arraySlice: String): List<String> {
        val slices = mutableListOf<String>()
        var objectDepth = 0
        var objectStart = -1

        walkJsonChars(arraySlice) { i, ch, inString ->
            if (!inString) {
                when (ch) {
                    '{' -> {
                        if (objectDepth == 0) objectStart = i
                        objectDepth++
                    }
                    '}' -> {
                        if (objectDepth > 0) {
                            objectDepth--
                            if (objectDepth == 0 && objectStart >= 0) {
                                slices += arraySlice.substring(objectStart, i + 1)
                                objectStart = -1
                            }
                        }
                    }
                }
            }
            i + 1
        }

        return slices
    }

    private fun nodeToInt(node: JsonNode): Int? = when {
        node.isInt -> node.asInt()
        node.isTextual -> node.asText("").toIntOrNull()
        else -> null
    }

    private fun parseConfidence(node: JsonNode): Double {
        return when {
            node.isNumber -> node.asDouble(Double.NaN)
            node.isTextual -> node.asText("").toDoubleOrNull() ?: Double.NaN
            else -> Double.NaN
        }
    }

    private fun normalizeConfidence(value: Double): Double {
        if (value.isNaN()) return 0.5
        if (value in 0.0..1.0) return value
        if (value > 1.0 && value <= 100.0) return value / 100.0
        return 0.5
    }
}

private fun MutableMap<Pair<String, String>, MutableList<BaseWordRow>>.removeById(
    key: Pair<String, String>,
    wordId: Int
) {
    val rows = this[key] ?: return
    rows.removeIf { it.wordId == wordId }
    if (rows.isEmpty()) remove(key)
}

private fun MutableMap<Pair<String, String>, MutableList<BaseWordRow>>.takeFirstFrom(
    key: Pair<String, String>
): BaseWordRow? {
    val rows = this[key]
    if (rows.isNullOrEmpty()) {
        remove(key)
        return null
    }
    val row = rows.removeAt(0)
    if (rows.isEmpty()) remove(key)
    return row
}

private fun MutableMap<Pair<String, String>, MutableList<BaseWordRow>>.takeFirstFuzzy(
    word: String,
    type: String
): BaseWordRow? {
    val matchKey = entries.firstOrNull { (key, rows) ->
        key.second == type && rows.isNotEmpty() && FuzzyWordMatcher.matches(key.first, word)
    }?.key ?: return null
    return takeFirstFrom(matchKey)
}
