package scrabble.phrases.tools.rarity

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

data class RunBaseline(
    val count: Int,
    val minId: Int?,
    val maxId: Int?
)

class RunCsvRepository(
    private val csv: CsvCodec = CsvCodec()
) {

    fun loadBaseRows(path: Path): List<BaseWordRow> {
        val table = csv.readTable(path)
        requireColumns(path, table.headers, BASE_CSV_HEADERS)

        return table.records
            .map { record ->
                val row = toRowMap(table.headers, record)
                BaseWordRow(
                    wordId = parseInt(path, record, row, "word_id"),
                    word = requireNonBlank(path, record, row, "word"),
                    type = requireNonBlank(path, record, row, "type")
                )
            }
            .sortedBy { it.wordId }
    }

    fun loadRunRows(path: Path): List<RunCsvRow> {
        if (!Files.exists(path)) return emptyList()

        val table = csv.readTable(path)
        requireColumns(path, table.headers, RUN_CSV_HEADERS)

        val byWordId = linkedMapOf<Int, RunCsvRow>()
        table.records.forEach { record ->
            val row = toRowMap(table.headers, record)
            val rarityLevel = parseInt(path, record, row, "rarity_level")
            val confidence = parseDouble(path, record, row, "confidence")

            if (rarityLevel !in 1..5) {
                throw CsvFormatException("rarity_level out of range at ${path.toAbsolutePath()}:${record.lineNumber}")
            }
            if (confidence < 0.0 || confidence > 1.0) {
                throw CsvFormatException("confidence out of range at ${path.toAbsolutePath()}:${record.lineNumber}")
            }

            val parsed = RunCsvRow(
                wordId = parseInt(path, record, row, "word_id"),
                word = requireNonBlank(path, record, row, "word"),
                type = requireNonBlank(path, record, row, "type"),
                rarityLevel = rarityLevel,
                tag = row["tag"].orEmpty(),
                confidence = confidence,
                scoredAt = row["scored_at"].orEmpty(),
                model = row["model"].orEmpty(),
                runSlug = row["run_slug"].orEmpty()
            )

            // Last occurrence wins for duplicate word_id.
            byWordId[parsed.wordId] = parsed
        }

        return byWordId.values.sortedBy { it.wordId }
    }

    fun appendRunRows(path: Path, rows: List<RunCsvRow>) {
        if (rows.isEmpty()) return

        path.parent?.let { Files.createDirectories(it) }
        val fileExists = Files.exists(path)
        val appendHeaders = resolveAppendHeaders(path, fileExists)

        Files.newBufferedWriter(path, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { writer ->
            if (!fileExists) {
                writer.write(appendHeaders.joinToString(",") { csv.escape(it) })
                writer.newLine()
            }

            rows.forEach { row ->
                writer.write(serializeForHeaders(row, appendHeaders).joinToString(",") { csv.escape(it) })
                writer.newLine()
            }
        }
    }

    fun computeBaseline(rows: Collection<RunCsvRow>): RunBaseline {
        if (rows.isEmpty()) return RunBaseline(0, null, null)

        val ids = rows.map { it.wordId }
        return RunBaseline(
            count = rows.size,
            minId = ids.min(),
            maxId = ids.max()
        )
    }

    fun mergeAndRewriteAtomic(path: Path, inMemoryRows: Collection<RunCsvRow>, baseline: RunBaseline) {
        val mergedByWordId = loadRunRows(path).associateBy { it.wordId }.toMutableMap()
        inMemoryRows.forEach { row -> mergedByWordId[row.wordId] = row }

        val mergedRows = mergedByWordId.values.sortedBy { it.wordId }
        assertNotShrunk(path, mergedRows, baseline)

        rewriteRunRowsAtomic(path, mergedRows)
    }

    fun rewriteRunRowsAtomic(path: Path, rows: Collection<RunCsvRow>) {
        val body = rows
            .sortedBy { it.wordId }
            .map { serializeForHeaders(it, RUN_CSV_HEADERS) }

        csv.writeTableAtomic(path, RUN_CSV_HEADERS, body)
    }

    fun loadFinalLevels(path: Path): Map<Int, Int> {
        val table = csv.readTable(path)
        require(table.headers.contains("word_id")) {
            "CSV ${path.toAbsolutePath()} is missing required column 'word_id'"
        }

        val levelColumn = when {
            table.headers.contains("final_level") -> "final_level"
            table.headers.contains("rarity_level") -> "rarity_level"
            table.headers.contains("median_level") -> "median_level"
            else -> error("CSV ${path.toAbsolutePath()} must contain one of: final_level, rarity_level, median_level")
        }

        val levels = mutableMapOf<Int, Int>()
        table.records.forEach { record ->
            val row = toRowMap(table.headers, record)
            val wordId = parseInt(path, record, row, "word_id")
            val level = parseInt(path, record, row, levelColumn)

            if (level !in 1..5) {
                throw CsvFormatException("$levelColumn out of range at ${path.toAbsolutePath()}:${record.lineNumber}")
            }
            levels[wordId] = level
        }

        return levels
    }

    fun writeRows(path: Path, headers: List<String>, rows: List<List<String>>) {
        csv.writeTable(path, headers, rows)
    }

    fun readTable(path: Path): CsvTable = csv.readTable(path)

    fun writeTableAtomic(path: Path, headers: List<String>, rows: List<List<String>>) {
        csv.writeTableAtomic(path, headers, rows)
    }

    private fun serializeForHeaders(row: RunCsvRow, headers: List<String>): List<String> {
        val base = mapOf(
            "word_id" to row.wordId.toString(),
            "word" to row.word,
            "type" to row.type,
            "rarity_level" to row.rarityLevel.toString(),
            "tag" to row.tag,
            "confidence" to row.confidence.toString(),
            "scored_at" to row.scoredAt,
            "model" to row.model,
            "run_slug" to row.runSlug
        )
        return headers.map { header -> base[header].orEmpty() }
    }

    private fun resolveAppendHeaders(path: Path, fileExists: Boolean): List<String> {
        if (!fileExists) return RUN_CSV_HEADERS

        val headerLine = Files.newBufferedReader(path, Charsets.UTF_8).use { reader -> reader.readLine() }
            ?: return RUN_CSV_HEADERS
        val headers = csv.parseLine(headerLine, lineNumber = 1)
        requireColumns(path, headers, RUN_CSV_HEADERS)
        return headers
    }

    private fun assertNotShrunk(path: Path, mergedRows: List<RunCsvRow>, baseline: RunBaseline) {
        val mergedCount = mergedRows.size
        if (mergedCount < baseline.count) {
            throw IllegalStateException(
                "Guarded rewrite aborted for ${path.toAbsolutePath()}: mergedCount=$mergedCount < baseline=${baseline.count}"
            )
        }

        val firstId = mergedRows.firstOrNull()?.wordId
        val lastId = mergedRows.lastOrNull()?.wordId

        if (baseline.minId != null && firstId != null && firstId > baseline.minId) {
            throw IllegalStateException(
                "Guarded rewrite aborted for ${path.toAbsolutePath()}: merged minId $firstId > baseline ${baseline.minId}"
            )
        }

        if (baseline.maxId != null && lastId != null && lastId < baseline.maxId) {
            throw IllegalStateException(
                "Guarded rewrite aborted for ${path.toAbsolutePath()}: merged maxId $lastId < baseline ${baseline.maxId}"
            )
        }
    }

    private fun toRowMap(headers: List<String>, record: CsvRecord): Map<String, String> {
        return headers.zip(record.values).toMap()
    }

    private fun parseInt(path: Path, record: CsvRecord, row: Map<String, String>, key: String): Int {
        return row[key]?.toIntOrNull()
            ?: throw CsvFormatException("Invalid $key at ${path.toAbsolutePath()}:${record.lineNumber}")
    }

    private fun parseDouble(path: Path, record: CsvRecord, row: Map<String, String>, key: String): Double {
        return row[key]?.toDoubleOrNull()
            ?: throw CsvFormatException("Invalid $key at ${path.toAbsolutePath()}:${record.lineNumber}")
    }

    private fun requireNonBlank(path: Path, record: CsvRecord, row: Map<String, String>, key: String): String {
        val value = row[key].orEmpty()
        if (value.isBlank()) {
            throw CsvFormatException("Blank $key at ${path.toAbsolutePath()}:${record.lineNumber}")
        }
        return value
    }

    private fun requireColumns(path: Path, headers: List<String>, required: List<String>) {
        val missing = required.filterNot(headers::contains)
        require(missing.isEmpty()) {
            "CSV ${path.toAbsolutePath()} is missing required columns: ${missing.joinToString(", ")}"
        }
    }
}
