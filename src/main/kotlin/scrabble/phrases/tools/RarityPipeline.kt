package scrabble.phrases.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt

private const val DEFAULT_LMSTUDIO_ENDPOINT = "http://127.0.0.1:1234/v1/chat/completions"
private const val DEFAULT_BATCH_SIZE = 40
private const val DEFAULT_MAX_RETRIES = 3
private const val DEFAULT_TIMEOUT_SECONDS = 90L
private const val DEFAULT_OUTLIER_THRESHOLD = 2
private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.55
private const val FALLBACK_RARITY_LEVEL = 4

private val mapper = ObjectMapper()

private data class WordRow(
    val wordId: Int,
    val word: String,
    val type: String
)

private data class ScoreResult(
    val wordId: Int,
    val word: String,
    val type: String,
    val rarityLevel: Int,
    val tag: String,
    val confidence: Double
)

fun main(args: Array<String>) {
    val (step, rawArgs) = resolveStep(args)
    val options = parseArgs(rawArgs)

    when (step) {
        "step1" -> step1Export()
        "step2" -> step2Score(options)
        "step3" -> step3Compare(options)
        "step4" -> step4Upload(options)
        else -> {
            error(
                "Unknown step '$step'. Use one of: step1, step2, step3, step4. " +
                    "You can also use Gradle tasks rarityStep1Export..rarityStep4Upload."
            )
        }
    }
}

private fun resolveStep(args: Array<String>): Pair<String, List<String>> {
    val stepFromProperty = System.getProperty("rarity.step")
    if (!stepFromProperty.isNullOrBlank()) {
        return stepFromProperty to args.toList()
    }

    if (args.isEmpty()) {
        error("Missing step. Usage: RarityPipelineKt <step1|step2|step3|step4> [options]")
    }

    return args[0] to args.drop(1)
}

private fun parseArgs(args: List<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val token = args[i]
        if (!token.startsWith("--")) {
            i++
            continue
        }

        val key = token.removePrefix("--")
        val value = if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
            i += 2
            args[i - 1]
        } else {
            i += 1
            "true"
        }
        out[key] = value
    }
    return out
}

private fun step1Export() {
    val outputDir = ensureRarityOutputDir()
    val csvPath = outputDir.resolve("step1_words.csv")

    withConnection { conn ->
        ensureWorkTable(conn)

        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                INSERT INTO word_rarity_work (word_id, word, type, exported_at)
                SELECT id, word, type, NOW()
                FROM words
                ON CONFLICT (word_id)
                DO UPDATE SET
                    word = EXCLUDED.word,
                    type = EXCLUDED.type,
                    exported_at = EXCLUDED.exported_at
                """.trimIndent()
            )
        }

        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT word_id, word, type, exported_at FROM word_rarity_work ORDER BY word_id").use { rs ->
                writeCsv(
                    csvPath,
                    listOf("word_id", "word", "type", "exported_at")
                ) { writer ->
                    while (rs.next()) {
                        writer.writeCsvRow(
                            listOf(
                                rs.getInt("word_id").toString(),
                                rs.getString("word"),
                                rs.getString("type"),
                                rs.getString("exported_at")
                            )
                        )
                    }
                }
            }
        }
    }

    println("Step 1 complete. Exported words to ${csvPath.toAbsolutePath()}")
}

private fun step2Score(options: Map<String, String>) {
    val runSlug = sanitizeRunSlug(requiredOption(options, "run"))
    val model = requiredOption(options, "model")
    val batchSize = options["batch-size"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_BATCH_SIZE
    val limit = options["limit"]?.toIntOrNull()?.takeIf { it > 0 }
    val maxRetries = options["max-retries"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_MAX_RETRIES
    val timeoutSeconds = options["timeout-seconds"]?.toLongOrNull()?.coerceAtLeast(5) ?: DEFAULT_TIMEOUT_SECONDS
    val endpoint = options["endpoint"] ?: System.getenv("LMSTUDIO_API_URL") ?: DEFAULT_LMSTUDIO_ENDPOINT
    val inputPath = options["input"]?.let { Paths.get(it) }

    val outputDir = ensureRarityOutputDir()
    val runsDir = outputDir.resolve("runs")
    val failedDir = outputDir.resolve("failed_batches")
    Files.createDirectories(runsDir)
    Files.createDirectories(failedDir)

    val runLogPath = runsDir.resolve("$runSlug.jsonl")
    val failedLogPath = failedDir.resolve("$runSlug.failed.jsonl")
    val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    val apiKey = System.getenv("LMSTUDIO_API_KEY")

    withConnection { conn ->
        ensureWorkTable(conn)
        ensureRunColumns(conn, runSlug)

        val levelCol = runLevelColumn(runSlug)
        val pendingWords = fetchPendingWords(conn, levelCol, inputPath, limit)

        if (pendingWords.isEmpty()) {
            println("Step 2 complete. No pending words for run '$runSlug'.")
            return@withConnection
        }

        var scoredCount = 0
        var failedCount = 0

        for (batch in pendingWords.chunked(batchSize)) {
            val scored = scoreBatchResilient(
                batch = batch,
                runSlug = runSlug,
                model = model,
                endpoint = endpoint,
                maxRetries = maxRetries,
                timeoutSeconds = timeoutSeconds,
                runLogPath = runLogPath,
                failedLogPath = failedLogPath,
                httpClient = httpClient,
                apiKey = apiKey
            )

            if (scored.isNotEmpty()) {
                persistScores(conn, runSlug, scored)
                scoredCount += scored.size
            }

            failedCount += (batch.size - scored.size)
        }

        println("Step 2 complete for run '$runSlug': scored=$scoredCount failed=$failedCount pending=${pendingWords.size}")
        println("Run log: ${runLogPath.toAbsolutePath()}")
        println("Failed log: ${failedLogPath.toAbsolutePath()}")
    }
}

private fun step3Compare(options: Map<String, String>) {
    val runs = requiredOption(options, "runs").split(',').map { sanitizeRunSlug(it.trim()) }.filter { it.isNotBlank() }
    require(runs.isNotEmpty()) { "--runs must contain at least one run slug" }

    val outlierThreshold = options["outlier-threshold"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_OUTLIER_THRESHOLD
    val confidenceThreshold = options["confidence-threshold"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: DEFAULT_CONFIDENCE_THRESHOLD

    val outputDir = ensureRarityOutputDir()
    val comparisonPath = outputDir.resolve("step3_comparison.csv")
    val outliersPath = outputDir.resolve("step3_outliers.csv")

    withConnection { conn ->
        ensureWorkTable(conn)
        runs.forEach { run ->
            require(columnExists(conn, runLevelColumn(run))) { "Missing run column ${runLevelColumn(run)}. Execute step2 for run '$run'." }
            require(columnExists(conn, runConfidenceColumn(run))) { "Missing run column ${runConfidenceColumn(run)}. Execute step2 for run '$run'." }
        }

        val selectDynamic = runs.joinToString(", ") { run ->
            "${quotedIdent(runLevelColumn(run))} AS ${quotedIdent(runLevelColumn(run))}, " +
                "${quotedIdent(runConfidenceColumn(run))} AS ${quotedIdent(runConfidenceColumn(run))}"
        }

        val sql = "SELECT word_id, word, type, $selectDynamic FROM word_rarity_work ORDER BY word_id"

        val comparisonHeaders = buildList {
            addAll(listOf("word_id", "word", "type"))
            runs.forEach { run ->
                add(runLevelColumn(run))
                add(runConfidenceColumn(run))
            }
            addAll(listOf("median_level", "spread", "is_outlier", "reason"))
        }

        var outlierCount = 0

        conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                writeCsv(comparisonPath, comparisonHeaders) { comparisonWriter ->
                    writeCsv(
                        outliersPath,
                        listOf("word_id", "word", "type", "median_level", "spread", "reason")
                    ) { outlierWriter ->
                        while (rs.next()) {
                            val levels = mutableMapOf<String, Int?>()
                            val confidences = mutableMapOf<String, Double?>()

                            runs.forEach { run ->
                                levels[run] = rs.getNullableInt(runLevelColumn(run))
                                confidences[run] = rs.getNullableDouble(runConfidenceColumn(run))
                            }

                            val availableLevels = levels.values.filterNotNull()
                            val medianLevel = if (availableLevels.isEmpty()) FALLBACK_RARITY_LEVEL else median(availableLevels)
                            val spread = if (availableLevels.size < 2) 0 else availableLevels.max() - availableLevels.min()
                            val lowConfidence = confidences.values.filterNotNull().any { it < confidenceThreshold }
                            val isOutlier = availableLevels.size >= 2 && (spread >= outlierThreshold || lowConfidence)

                            val reasons = mutableListOf<String>()
                            if (spread >= outlierThreshold) reasons.add("spread>=$outlierThreshold")
                            if (lowConfidence) reasons.add("low_confidence<$confidenceThreshold")
                            val reason = reasons.joinToString(";")

                            val row = buildList {
                                add(rs.getInt("word_id").toString())
                                add(rs.getString("word"))
                                add(rs.getString("type"))
                                runs.forEach { run ->
                                    add(levels[run]?.toString() ?: "")
                                    add(confidences[run]?.toString() ?: "")
                                }
                                add(medianLevel.toString())
                                add(spread.toString())
                                add(isOutlier.toString())
                                add(reason)
                            }

                            comparisonWriter.writeCsvRow(row)

                            if (isOutlier) {
                                outlierCount++
                                outlierWriter.writeCsvRow(
                                    listOf(
                                        rs.getInt("word_id").toString(),
                                        rs.getString("word"),
                                        rs.getString("type"),
                                        medianLevel.toString(),
                                        spread.toString(),
                                        reason
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        println("Step 3 complete. Outliers=$outlierCount")
        println("Comparison: ${comparisonPath.toAbsolutePath()}")
        println("Outliers: ${outliersPath.toAbsolutePath()}")
    }
}

private fun step4Upload(options: Map<String, String>) {
    val runs = requiredOption(options, "runs").split(',').map { sanitizeRunSlug(it.trim()) }.filter { it.isNotBlank() }
    require(runs.isNotEmpty()) { "--runs must contain at least one run slug" }

    val outputDir = ensureRarityOutputDir()
    val reportPath = outputDir.resolve("step4_upload_report.csv")

    withConnection { conn ->
        ensureWorkTable(conn)
        runs.forEach { run ->
            require(columnExists(conn, runLevelColumn(run))) { "Missing run column ${runLevelColumn(run)}. Execute step2 for run '$run'." }
        }

        val selectDynamic = runs.joinToString(", ") { run ->
            "wrw.${quotedIdent(runLevelColumn(run))} AS ${quotedIdent(runLevelColumn(run))}"
        }

        val sql =
            "SELECT w.id AS word_id, w.word, w.type, w.rarity_level AS current_level, $selectDynamic " +
                "FROM words w LEFT JOIN word_rarity_work wrw ON wrw.word_id = w.id ORDER BY w.id"

        conn.autoCommit = false
        try {
            val updateStmt = conn.prepareStatement("UPDATE words SET rarity_level=? WHERE id=?")

            var updated = 0
            writeCsv(
                reportPath,
                listOf("word_id", "word", "type", "current_level", "new_level", "runs_used")
            ) { writer ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            val levels = runs.mapNotNull { run -> rs.getNullableInt(runLevelColumn(run)) }
                            val newLevel = if (levels.isEmpty()) FALLBACK_RARITY_LEVEL else median(levels)
                            val currentLevel = rs.getInt("current_level")

                            updateStmt.setInt(1, newLevel)
                            updateStmt.setInt(2, rs.getInt("word_id"))
                            updateStmt.addBatch()
                            updated++

                            writer.writeCsvRow(
                                listOf(
                                    rs.getInt("word_id").toString(),
                                    rs.getString("word"),
                                    rs.getString("type"),
                                    currentLevel.toString(),
                                    newLevel.toString(),
                                    levels.size.toString()
                                )
                            )
                        }
                    }
                }
            }

            updateStmt.executeBatch()
            conn.commit()
            println("Step 4 complete. Updated $updated words.")
            println("Upload report: ${reportPath.toAbsolutePath()}")
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }
}

private fun fetchPendingWords(
    conn: Connection,
    levelColumn: String,
    inputPath: Path?,
    limit: Int?
): List<WordRow> {
    val idsFilter = inputPath?.let { loadWordIdsFromCsv(it) } ?: emptySet()
    val sql = buildString {
        append("SELECT word_id, word, type FROM word_rarity_work WHERE ")
        append(quotedIdent(levelColumn))
        append(" IS NULL ORDER BY word_id")
    }

    val rows = mutableListOf<WordRow>()
    conn.createStatement().use { stmt ->
        stmt.executeQuery(sql).use { rs ->
            while (rs.next()) {
                val row = WordRow(
                    wordId = rs.getInt("word_id"),
                    word = rs.getString("word"),
                    type = rs.getString("type")
                )
                if (idsFilter.isEmpty() || idsFilter.contains(row.wordId)) {
                    rows.add(row)
                    if (limit != null && rows.size >= limit) break
                }
            }
        }
    }

    return rows
}

private fun scoreBatchResilient(
    batch: List<WordRow>,
    runSlug: String,
    model: String,
    endpoint: String,
    maxRetries: Int,
    timeoutSeconds: Long,
    runLogPath: Path,
    failedLogPath: Path,
    httpClient: HttpClient,
    apiKey: String?
): List<ScoreResult> {
    val direct = tryScoreBatch(
        batch,
        runSlug,
        model,
        endpoint,
        maxRetries,
        timeoutSeconds,
        runLogPath,
        httpClient,
        apiKey
    )
    if (direct != null) return direct

    if (batch.size == 1) {
        appendJsonLine(
            failedLogPath,
            mapOf(
                "ts" to OffsetDateTime.now().toString(),
                "run" to runSlug,
                "word_id" to batch.first().wordId,
                "word" to batch.first().word,
                "type" to batch.first().type,
                "error" to "batch_failed_after_retries"
            )
        )
        return emptyList()
    }

    val splitIndex = batch.size / 2
    val left = scoreBatchResilient(
        batch.subList(0, splitIndex),
        runSlug,
        model,
        endpoint,
        maxRetries,
        timeoutSeconds,
        runLogPath,
        failedLogPath,
        httpClient,
        apiKey
    )
    val right = scoreBatchResilient(
        batch.subList(splitIndex, batch.size),
        runSlug,
        model,
        endpoint,
        maxRetries,
        timeoutSeconds,
        runLogPath,
        failedLogPath,
        httpClient,
        apiKey
    )
    return left + right
}

private fun tryScoreBatch(
    batch: List<WordRow>,
    runSlug: String,
    model: String,
    endpoint: String,
    maxRetries: Int,
    timeoutSeconds: Long,
    runLogPath: Path,
    httpClient: HttpClient,
    apiKey: String?
): List<ScoreResult>? {
    var lastError: String? = null

    repeat(maxRetries) { attempt ->
        val requestPayload = buildLmRequest(model, batch)
        try {
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")

            if (!apiKey.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val response = httpClient.send(
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestPayload)).build(),
                HttpResponse.BodyHandlers.ofString()
            )

            if (response.statusCode() !in 200..299) {
                throw IllegalStateException("HTTP ${response.statusCode()}: ${response.body()}")
            }

            val parsed = parseLmResponse(batch, response.body())
            appendJsonLine(
                runLogPath,
                mapOf(
                    "ts" to OffsetDateTime.now().toString(),
                    "run" to runSlug,
                    "attempt" to (attempt + 1),
                    "batch_size" to batch.size,
                    "request" to mapper.readTree(requestPayload),
                    "response" to mapper.readTree(response.body())
                )
            )
            return parsed
        } catch (e: Exception) {
            lastError = e.message ?: e::class.simpleName.orEmpty()
            appendJsonLine(
                runLogPath,
                mapOf(
                    "ts" to OffsetDateTime.now().toString(),
                    "run" to runSlug,
                    "attempt" to (attempt + 1),
                    "batch_size" to batch.size,
                    "error" to lastError,
                    "request" to requestPayload
                )
            )
        }
    }

    println("Batch failed after retries (size=${batch.size}): $lastError")
    return null
}

private fun buildLmRequest(model: String, batch: List<WordRow>): String {
    val entriesJson = mapper.writeValueAsString(batch.map { mapOf("word" to it.word, "type" to it.type) })
    val userPrompt =
        """
        Returnează DOAR JSON cu schema:
        {
          "results": [
            {
              "word": "string",
              "type": "N|A|V",
              "rarity_level": 1,
              "tag": "common|less_common|rare|technical|regional|archaic|uncertain",
              "confidence": 0.0
            }
          ]
        }

        Cerințe:
        - Un element rezultat pentru fiecare intrare.
        - Păstrează ordinea intrărilor.
        - rarity_level trebuie să fie întreg 1..5.
        - confidence între 0.0 și 1.0.
        - Fără text înainte/după JSON.

        Intrări:
        $entriesJson
        """.trimIndent()

    val payload = mapOf(
        "model" to model,
        "temperature" to 0,
        "messages" to listOf(
            mapOf("role" to "system", "content" to SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userPrompt)
        ),
        "response_format" to mapOf("type" to "json_object")
    )

    return mapper.writeValueAsString(payload)
}

private fun parseLmResponse(batch: List<WordRow>, responseBody: String): List<ScoreResult> {
    val root = mapper.readTree(responseBody)
    val content = root.path("choices").path(0).path("message").path("content").asText(null)
        ?: throw IllegalStateException("LMStudio response missing choices[0].message.content")

    val contentJson = mapper.readTree(content)
    val results = contentJson.path("results")
    if (!results.isArray) {
        throw IllegalStateException("LMStudio content is missing 'results' array")
    }
    if (results.size() != batch.size) {
        throw IllegalStateException("Result size mismatch: expected ${batch.size}, got ${results.size()}")
    }

    val parsed = mutableListOf<ScoreResult>()
    results.forEachIndexed { index, node ->
        parsed.add(parseScoreNode(batch[index], node))
    }
    return parsed
}

private fun parseScoreNode(expected: WordRow, node: JsonNode): ScoreResult {
    val word = node.path("word").asText("")
    val type = node.path("type").asText("")
    val rarity = node.path("rarity_level").asInt(-1)
    val tag = node.path("tag").asText("uncertain")
    val confidence = node.path("confidence").asDouble(Double.NaN)

    if (word != expected.word || type != expected.type) {
        throw IllegalStateException("Result order/content mismatch for word_id=${expected.wordId}: expected ${expected.word}/${expected.type}, got $word/$type")
    }
    if (rarity !in 1..5) {
        throw IllegalStateException("Invalid rarity_level '$rarity' for word '${expected.word}'")
    }
    if (confidence.isNaN() || confidence < 0.0 || confidence > 1.0) {
        throw IllegalStateException("Invalid confidence '$confidence' for word '${expected.word}'")
    }

    return ScoreResult(
        wordId = expected.wordId,
        word = expected.word,
        type = expected.type,
        rarityLevel = rarity,
        tag = tag.take(16),
        confidence = confidence
    )
}

private fun persistScores(conn: Connection, runSlug: String, scores: List<ScoreResult>) {
    if (scores.isEmpty()) return

    val levelCol = quotedIdent(runLevelColumn(runSlug))
    val tagCol = quotedIdent(runTagColumn(runSlug))
    val confidenceCol = quotedIdent(runConfidenceColumn(runSlug))
    val scoredAtCol = quotedIdent(runScoredAtColumn(runSlug))

    val sql =
        "UPDATE word_rarity_work SET $levelCol=?, $tagCol=?, $confidenceCol=?, $scoredAtCol=NOW() WHERE word_id=?"

    conn.prepareStatement(sql).use { stmt ->
        scores.forEach { score ->
            stmt.setInt(1, score.rarityLevel)
            stmt.setString(2, score.tag)
            stmt.setDouble(3, score.confidence)
            stmt.setInt(4, score.wordId)
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
}

private fun requiredOption(options: Map<String, String>, key: String): String =
    options[key]?.takeIf { it.isNotBlank() }
        ?: error("Missing required option --$key")

private fun sanitizeRunSlug(raw: String): String {
    val normalized = raw.trim().lowercase().replace('-', '_')
    require(normalized.matches(Regex("[a-z0-9_]{1,40}"))) {
        "Invalid run slug '$raw'. Allowed pattern: [a-z0-9_]{1,40}"
    }
    return normalized
}

private fun median(values: List<Int>): Int {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
    }
}

private fun runLevelColumn(runSlug: String) = "run_${runSlug}_level"
private fun runTagColumn(runSlug: String) = "run_${runSlug}_tag"
private fun runConfidenceColumn(runSlug: String) = "run_${runSlug}_confidence"
private fun runScoredAtColumn(runSlug: String) = "run_${runSlug}_scored_at"

private fun ensureWorkTable(conn: Connection) {
    conn.createStatement().use { stmt ->
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS word_rarity_work (
                word_id INTEGER PRIMARY KEY,
                word VARCHAR(50) NOT NULL,
                type CHAR(1) NOT NULL,
                exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """.trimIndent()
        )
    }
}

private fun ensureRunColumns(conn: Connection, runSlug: String) {
    val levelCol = quotedIdent(runLevelColumn(runSlug))
    val tagCol = quotedIdent(runTagColumn(runSlug))
    val confidenceCol = quotedIdent(runConfidenceColumn(runSlug))
    val scoredAtCol = quotedIdent(runScoredAtColumn(runSlug))

    conn.createStatement().use { stmt ->
        stmt.execute("ALTER TABLE word_rarity_work ADD COLUMN IF NOT EXISTS $levelCol SMALLINT CHECK ($levelCol BETWEEN 1 AND 5)")
        stmt.execute("ALTER TABLE word_rarity_work ADD COLUMN IF NOT EXISTS $tagCol VARCHAR(16)")
        stmt.execute("ALTER TABLE word_rarity_work ADD COLUMN IF NOT EXISTS $confidenceCol REAL")
        stmt.execute("ALTER TABLE word_rarity_work ADD COLUMN IF NOT EXISTS $scoredAtCol TIMESTAMPTZ")
    }
}

private fun quotedIdent(identifier: String): String = "\"${identifier.replace("\"", "\"\"")}\""

private fun columnExists(conn: Connection, columnName: String): Boolean {
    conn.prepareStatement(
        "SELECT 1 FROM information_schema.columns WHERE table_name='word_rarity_work' AND column_name=? LIMIT 1"
    ).use { stmt ->
        stmt.setString(1, columnName)
        stmt.executeQuery().use { rs ->
            return rs.next()
        }
    }
}

private fun appendJsonLine(path: Path, payload: Any) {
    val line = mapper.writeValueAsString(payload) + "\n"
    Files.writeString(path, line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
}

private fun writeCsv(path: Path, headers: List<String>, block: (java.io.BufferedWriter) -> Unit) {
    Files.createDirectories(path.parent)
    Files.newBufferedWriter(path).use { writer ->
        writer.writeCsvRow(headers)
        block(writer)
    }
}

private fun java.io.BufferedWriter.writeCsvRow(values: List<String>) {
    write(values.joinToString(",") { csvEscape(it) })
    newLine()
}

private fun csvEscape(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun loadWordIdsFromCsv(path: Path): Set<Int> {
    if (!Files.exists(path)) return emptySet()

    val lines = Files.readAllLines(path)
    if (lines.isEmpty()) return emptySet()

    val header = lines.first().split(',').map { it.trim().trim('"') }
    val idIndex = header.indexOf("word_id")
    if (idIndex == -1) return emptySet()

    return lines.drop(1)
        .mapNotNull { line ->
            val cols = line.split(',')
            if (cols.size <= idIndex) return@mapNotNull null
            cols[idIndex].trim().trim('"').toIntOrNull()
        }
        .toSet()
}

private fun ResultSet.getNullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

private fun ResultSet.getNullableDouble(column: String): Double? {
    val value = getDouble(column)
    return if (wasNull()) null else value
}

private fun ensureRarityOutputDir(): Path {
    val outputDir = Paths.get("build", "rarity")
    Files.createDirectories(outputDir)
    return outputDir
}

private inline fun withConnection(block: (Connection) -> Unit) {
    val dbUrl = System.getenv("SUPABASE_DB_URL") ?: "jdbc:postgresql://localhost:5432/postgres"
    val dbUser = System.getenv("SUPABASE_DB_USER") ?: "postgres"
    val dbPassword = System.getenv("SUPABASE_DB_PASSWORD") ?: ""

    DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
        block(conn)
    }
}

private val SYSTEM_PROMPT =
    """
    Ești evaluator lexical pentru limba română.
    Evaluezi “stranietatea” unui cuvânt pe scară 1..5 pentru un vorbitor român contemporan din România.

    Scară:
    1 = uzual de bază (foarte frecvent, cotidian)
    2 = uzual extins (încă frecvent, puțin marcat)
    3 = mai puțin uzual (neobișnuit dar înțeles larg)
    4 = rar/specializat/regional (în afara uzului comun)
    5 = foarte rar/arhaic/regional puternic (obscur sau vechi)

    Reguli:
    - Arhaisme: de obicei 5.
    - Termeni tehnici: 4 sau 5 în funcție de cât de cunoscuți sunt în afara domeniului.
    - Regionalisme: 4 sau 5 în funcție de răspândire.
    - La limită între 1 și 3 => 2.
    - La limită între 3 și 5 => 4.
    - Evaluează forma lexicală ca atare, fără context de propoziție.
    - Nu inventa câmpuri.
    - Răspunde strict JSON valid, fără explicații în afara JSON.
    """.trimIndent()
