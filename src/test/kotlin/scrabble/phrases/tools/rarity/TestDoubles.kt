package scrabble.phrases.tools.rarity

class InMemoryWordStore(initialLevels: Map<Int, Int>) : WordStore {
    val levels: MutableMap<Int, Int> = initialLevels.toMutableMap()
    val updatesHistory: MutableList<Map<Int, Int>> = mutableListOf()

    override fun fetchAllWords(): List<BaseWordRow> {
        return levels.keys.sorted().map { BaseWordRow(it, "word_$it", "N") }
    }

    override fun fetchAllWordLevels(): List<WordLevel> {
        return levels.entries.sortedBy { it.key }.map { WordLevel(it.key, it.value) }
    }

    override fun updateRarityLevels(updates: Map<Int, Int>) {
        updatesHistory += updates.toMap()
        updates.forEach { (wordId, level) -> levels[wordId] = level }
    }
}

class FakeLmClient(
    private val resolver: (BaseWordRow) -> ScoreResult = {
        ScoreResult(
            wordId = it.wordId,
            word = it.word,
            type = it.type,
            rarityLevel = ((it.wordId - 1) % 5) + 1,
            tag = "common",
            confidence = 0.9
        )
    }
) : LmClient {
    var scoreCalls: Int = 0

    override fun resolveEndpoint(endpointOption: String?, baseUrlOption: String?): ResolvedEndpoint {
        return ResolvedEndpoint(
            endpoint = "http://127.0.0.1:1234/v1/chat/completions",
            modelsEndpoint = "http://127.0.0.1:1234/v1/models",
            flavor = LmApiFlavor.OPENAI_COMPAT,
            source = "test"
        )
    }

    override fun preflight(resolvedEndpoint: ResolvedEndpoint, model: String) {
        // no-op for tests
    }

    override fun scoreBatchResilient(
        batch: List<BaseWordRow>,
        context: ScoringContext
    ): List<ScoreResult> {
        scoreCalls++
        return batch.map(resolver)
    }
}

fun testRunRow(
    id: Int,
    rarityLevel: Int = 3,
    confidence: Double = 0.8,
    model: String = "m",
    runSlug: String = "r",
    word: String = "word_$id",
    type: String = "N",
    tag: String = "rare"
): RunCsvRow {
    return RunCsvRow(
        wordId = id,
        word = word,
        type = type,
        rarityLevel = rarityLevel,
        tag = tag,
        confidence = confidence,
        scoredAt = "2026-02-07T00:00:00Z",
        model = model,
        runSlug = runSlug
    )
}

fun CsvTable.toRowMaps(): List<Map<String, String>> {
    return records.map { headers.zip(it.values).toMap() }
}
