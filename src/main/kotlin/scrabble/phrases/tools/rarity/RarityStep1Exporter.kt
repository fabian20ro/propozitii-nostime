package scrabble.phrases.tools.rarity

import java.nio.file.Path

class RarityStep1Exporter(
    private val wordStore: WordStore,
    private val runCsvRepository: RunCsvRepository,
    private val outputDir: Path = ensureRarityOutputDir()
) {
    fun execute(): Path {
        val csvPath = outputDir.resolve("step1_words.csv")
        val words = wordStore.fetchAllWords().sortedBy { it.wordId }
        val rows = words.map { listOf(it.wordId.toString(), it.word, it.type) }
        runCsvRepository.writeRows(csvPath, BASE_CSV_HEADERS, rows)

        println("Step 1 complete. Exported ${words.size} words to ${csvPath.toAbsolutePath()}")
        return csvPath
    }
}
