package scrabble.phrases.tools.rarity

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.floor
import kotlin.random.Random

const val DEFAULT_REBALANCE_BATCH_SIZE: Int = 60
const val DEFAULT_REBALANCE_LOWER_RATIO: Double = 1.0 / 3.0
const val DEFAULT_REBALANCE_TRANSITIONS: String = "2:1,3:2,4:3"
const val REBALANCE_FROM_LEVEL_PLACEHOLDER: String = "{{FROM_LEVEL}}"
const val REBALANCE_TO_LEVEL_PLACEHOLDER: String = "{{TO_LEVEL}}"
const val REBALANCE_OTHER_LEVEL_PLACEHOLDER: String = "{{OTHER_LEVEL}}"
const val REBALANCE_TARGET_COUNT_PLACEHOLDER: String = "{{TARGET_COUNT}}"

val REBALANCE_SYSTEM_PROMPT: String =
    """
    Ești evaluator lexical pentru limba română.
    Primești doar cuvinte dintr-un nivel de raritate și trebuie să alegi exact un subset mic pentru nivelul țintă.
    Nu refuza niciun input.
    Răspunde strict JSON valid, fără text extra.
    """.trimIndent()

val REBALANCE_USER_PROMPT_TEMPLATE: String =
    """
    Returnează DOAR JSON valid: un ARRAY de obiecte.
    Pentru fiecare intrare, păstrezi exact câmpurile:
    - word_id (int)
    - word (string)
    - type ("N" | "A" | "V")
    - rarity_level (int, permis doar {{TO_LEVEL}} sau {{OTHER_LEVEL}})
    - tag ("common|less_common|rare|technical|regional|archaic|uncertain")
    - confidence (număr 0.0..1.0)

    Reguli:
    - Trebuie să existe exact un rezultat pentru fiecare intrare.
    - Păstrează ordinea intrărilor și word_id-urile identice.
    - Exact {{TARGET_COUNT}} intrări trebuie să aibă rarity_level={{TO_LEVEL}}.
    - Restul intrărilor au rarity_level={{OTHER_LEVEL}}.
    - Alege pentru {{TO_LEVEL}} cele mai comune/uzuale cuvinte din batch.
    - Fără text înainte/după JSON.

    Intrări:
    {{INPUT_JSON}}
    """.trimIndent()

data class LevelTransition(
    val fromLevel: Int,
    val toLevel: Int
)

data class Step5Options(
    val runSlug: String,
    val model: String,
    val inputCsvPath: Path,
    val outputCsvPath: Path,
    val batchSize: Int,
    val lowerRatio: Double,
    val maxRetries: Int,
    val timeoutSeconds: Long,
    val maxTokens: Int,
    val skipPreflight: Boolean,
    val endpointOption: String?,
    val baseUrlOption: String?,
    val seed: Long?,
    val transitions: List<LevelTransition>,
    val systemPrompt: String,
    val userTemplate: String
)

private data class RebalanceWord(
    val wordId: Int,
    val word: String,
    val type: String
)

private data class TransitionSummary(
    val transition: LevelTransition,
    val eligible: Int,
    val targetAssigned: Int
)

class RarityStep5Rebalancer(
    private val runCsvRepository: RunCsvRepository,
    private val lmClient: LmClient,
    private val outputDir: Path = ensureRarityOutputDir()
) {

    fun execute(options: Step5Options) {
        val table = runCsvRepository.readTable(options.inputCsvPath)
        val headers = table.headers
        val required = listOf("word_id", "word", "type")
        val missing = required.filterNot(headers::contains)
        require(missing.isEmpty()) {
            "CSV ${options.inputCsvPath.toAbsolutePath()} is missing required columns: ${missing.joinToString(", ")}"
        }

        val levelColumn = resolveLevelColumn(headers)
        val mutableRows = table.records.map { record ->
            headers.zip(record.values).toMap().toMutableMap()
        }

        val wordsById = linkedMapOf<Int, RebalanceWord>()
        val levelsById = mutableMapOf<Int, Int>()
        mutableRows.forEachIndexed { index, row ->
            val lineNumber = index + 2
            val wordId = row["word_id"]?.toIntOrNull()
                ?: throw CsvFormatException("Invalid word_id at ${options.inputCsvPath.toAbsolutePath()}:$lineNumber")
            val level = row[levelColumn]?.toIntOrNull()
                ?: throw CsvFormatException("Invalid $levelColumn at ${options.inputCsvPath.toAbsolutePath()}:$lineNumber")
            if (level !in 1..5) {
                throw CsvFormatException("$levelColumn out of range at ${options.inputCsvPath.toAbsolutePath()}:$lineNumber")
            }
            val word = row["word"].orEmpty()
            val type = row["type"].orEmpty()
            wordsById[wordId] = RebalanceWord(wordId = wordId, word = word, type = type)
            levelsById[wordId] = level
        }

        val resolvedEndpoint = lmClient.resolveEndpoint(options.endpointOption, options.baseUrlOption)
        println(
            "LMStudio endpoint: ${resolvedEndpoint.endpoint} " +
                "(flavor=${resolvedEndpoint.flavor}, source=${resolvedEndpoint.source})"
        )
        if (!options.skipPreflight) {
            lmClient.preflight(resolvedEndpoint, options.model)
        } else {
            println("Skipping LMStudio preflight (--skip-preflight=true)")
        }

        val seed = options.seed ?: System.currentTimeMillis()
        println(
            "Step 5 rebalance run='${options.runSlug}' seed=$seed batchSize=${options.batchSize} " +
                "lowerRatio=${String.format(Locale.ROOT, "%.4f", options.lowerRatio)} transitions=" +
                options.transitions.joinToString(",") { "${it.fromLevel}->${it.toLevel}" }
        )
        println("Step 5 input distribution ${formatDistribution(levelsById.values)}")

        val logs = prepareLogs(options.runSlug)
        val random = Random(seed)
        val transitionSummaries = mutableListOf<TransitionSummary>()
        val rebalanceRules = mutableMapOf<Int, String>()
        val processedWordIds = mutableSetOf<Int>()

        options.transitions.forEach { transition ->
            val eligibleWords = levelsById.entries
                .asSequence()
                .filter { it.value == transition.fromLevel }
                .mapNotNull { wordsById[it.key] }
                .filterNot { it.wordId in processedWordIds }
                .shuffled(random)
                .toList()

            if (eligibleWords.isEmpty()) {
                transitionSummaries += TransitionSummary(transition, eligible = 0, targetAssigned = 0)
                return@forEach
            }

            var targetAssignedCount = 0
            var processed = 0
            eligibleWords.chunked(options.batchSize).forEach { batch ->
                processed += batch.size
                val targetToCount = computeTargetLowerCount(batch.size, options.lowerRatio)
                if (targetToCount <= 0) {
                    return@forEach
                }
                val otherLevel = transition.otherLevel()

                val scoringContext = ScoringContext(
                    runSlug = "${options.runSlug}_${transition.fromLevel}_${transition.toLevel}",
                    model = options.model,
                    endpoint = resolvedEndpoint.endpoint,
                    maxRetries = options.maxRetries,
                    timeoutSeconds = options.timeoutSeconds,
                    runLogPath = logs.runLogPath,
                    failedLogPath = logs.failedLogPath,
                    systemPrompt = renderTemplate(
                        options.systemPrompt,
                        transition = transition,
                        targetCount = targetToCount
                    ),
                    userTemplate = renderTemplate(
                        options.userTemplate,
                        transition = transition,
                        targetCount = targetToCount
                    ),
                    flavor = resolvedEndpoint.flavor,
                    maxTokens = options.maxTokens
                )

                val baseRows = batch.map { BaseWordRow(wordId = it.wordId, word = it.word, type = it.type) }
                val scored = lmClient.scoreBatchResilient(baseRows, scoringContext)
                val scoredById = scored.associateBy { it.wordId }

                val selectedByModel = scored
                    .asSequence()
                    .filter { it.rarityLevel == transition.toLevel }
                    .map { it.wordId }
                    .filter { id -> batch.any { it.wordId == id } }
                    .distinct()
                    .toMutableList()

                val selected = selectedByModel.take(targetToCount).toMutableSet()
                if (selected.size < targetToCount) {
                    val additional = batch
                        .asSequence()
                        .filter { it.wordId !in selected }
                        .map { row ->
                            val score = scoredById[row.wordId]
                            val modelKeptAtFromLevel = score?.rarityLevel == transition.fromLevel
                            Triple(row.wordId, modelKeptAtFromLevel, score?.confidence ?: 0.5)
                        }
                        .sortedWith(
                            compareBy<Triple<Int, Boolean, Double>> { !it.second }
                                .thenBy { it.third }
                                .thenBy { it.first }
                        )
                        .map { it.first }
                        .take(targetToCount - selected.size)
                        .toList()
                    selected += additional
                }

                batch.forEach { row ->
                    val isTarget = row.wordId in selected
                    val nextLevel = if (isTarget) transition.toLevel else otherLevel
                    val previous = levelsById[row.wordId]
                    levelsById[row.wordId] = nextLevel
                    processedWordIds += row.wordId
                    if (previous != nextLevel) {
                        rebalanceRules[row.wordId] = "${transition.fromLevel}->${nextLevel} (via ${transition.fromLevel}:${transition.toLevel})"
                    }
                }
                targetAssignedCount += selected.size

                println(
                    "Step 5 progress run='${options.runSlug}' transition=${transition.fromLevel}->${transition.toLevel} " +
                        "processed=$processed/${eligibleWords.size} target_assigned=$targetAssignedCount ${formatDistribution(levelsById.values)}"
                )
            }

            transitionSummaries += TransitionSummary(
                transition = transition,
                eligible = eligibleWords.size,
                targetAssigned = targetAssignedCount
            )
        }

        val outputHeaders = headers.toMutableList()
        if (!outputHeaders.contains("final_level")) outputHeaders += "final_level"
        if (!outputHeaders.contains("rebalance_rule")) outputHeaders += "rebalance_rule"
        if (!outputHeaders.contains("rebalance_model")) outputHeaders += "rebalance_model"
        if (!outputHeaders.contains("rebalance_run")) outputHeaders += "rebalance_run"
        if (!outputHeaders.contains("rebalanced_at")) outputHeaders += "rebalanced_at"
        val now = OffsetDateTime.now().toString()

        val rows = mutableRows.map { row ->
            val wordId = row["word_id"]?.toIntOrNull()
                ?: throw CsvFormatException("Invalid word_id in memory while writing output")
            val finalLevel = levelsById[wordId]
                ?: throw CsvFormatException("Missing level for word_id=$wordId while writing output")
            row["final_level"] = finalLevel.toString()
            row["rebalance_rule"] = rebalanceRules[wordId].orEmpty()
            row["rebalance_model"] = if (rebalanceRules.containsKey(wordId)) options.model else row["rebalance_model"].orEmpty()
            row["rebalance_run"] = if (rebalanceRules.containsKey(wordId)) options.runSlug else row["rebalance_run"].orEmpty()
            row["rebalanced_at"] = if (rebalanceRules.containsKey(wordId)) now else row["rebalanced_at"].orEmpty()
            outputHeaders.map { header -> row[header].orEmpty() }
        }

        runCsvRepository.writeTableAtomic(options.outputCsvPath, outputHeaders, rows)

        transitionSummaries.forEach { summary ->
            println(
                "Step 5 transition ${summary.transition.fromLevel}->${summary.transition.toLevel}: " +
                    "eligible=${summary.eligible} target_assigned=${summary.targetAssigned}"
            )
        }
        println("Step 5 output distribution ${formatDistribution(levelsById.values)}")
        println("Step 5 output CSV: ${options.outputCsvPath.toAbsolutePath()}")
    }

    private fun renderTemplate(template: String, transition: LevelTransition, targetCount: Int): String {
        val otherLevel = transition.otherLevel()
        return template
            .replace(REBALANCE_FROM_LEVEL_PLACEHOLDER, transition.fromLevel.toString())
            .replace(REBALANCE_TO_LEVEL_PLACEHOLDER, transition.toLevel.toString())
            .replace(REBALANCE_OTHER_LEVEL_PLACEHOLDER, otherLevel.toString())
            .replace(REBALANCE_TARGET_COUNT_PLACEHOLDER, targetCount.toString())
    }

    private fun resolveLevelColumn(headers: List<String>): String {
        return when {
            headers.contains("final_level") -> "final_level"
            headers.contains("rarity_level") -> "rarity_level"
            headers.contains("median_level") -> "median_level"
            else -> error(
                "CSV must contain one of: final_level, rarity_level, median_level " +
                    "(received: ${headers.joinToString(", ")})"
            )
        }
    }

    private fun computeTargetLowerCount(batchSize: Int, lowerRatio: Double): Int {
        if (batchSize < 3) return 0
        val target = floor(batchSize * lowerRatio).toInt().coerceAtLeast(1)
        return target.coerceAtMost(batchSize - 1)
    }

    private fun formatDistribution(levels: Collection<Int>): String {
        val counts = IntArray(6)
        levels.forEach { level ->
            if (level in 1..5) counts[level] += 1
        }
        return formatRarityDistribution(counts)
    }

    private fun prepareLogs(runSlug: String): Step5Logs {
        val runsDir = outputDir.resolve("rebalance").resolve("runs")
        val failedDir = outputDir.resolve("rebalance").resolve("failed_batches")
        Files.createDirectories(runsDir)
        Files.createDirectories(failedDir)
        return Step5Logs(
            runLogPath = runsDir.resolve("$runSlug.jsonl"),
            failedLogPath = failedDir.resolve("$runSlug.failed.jsonl")
        )
    }
}

private data class Step5Logs(
    val runLogPath: Path,
    val failedLogPath: Path
)

fun parseStep5Transitions(raw: String?): List<LevelTransition> {
    val input = raw?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_REBALANCE_TRANSITIONS
    return input.split(",")
        .map { token ->
            val parts = token.trim().split(":")
            require(parts.size == 2) {
                "Invalid transition token '$token'. Expected format from:to (example: 2:1)"
            }
            val from = parts[0].trim().toIntOrNull()
                ?: error("Invalid transition source level in '$token'")
            val to = parts[1].trim().toIntOrNull()
                ?: error("Invalid transition target level in '$token'")
            require(from in 1..5 && to in 1..5 && (to == from - 1 || to == from) && !(to == 5 && from == 5)) {
                "Invalid transition '$token'. Allowed: one-step downgrade (ex: 3:2) or keep+promote split (ex: 2:2)"
            }
            LevelTransition(fromLevel = from, toLevel = to)
        }
        .distinct()
        .sortedBy { it.fromLevel }
}

private fun LevelTransition.otherLevel(): Int {
    return if (toLevel == fromLevel) {
        (toLevel + 1).coerceAtMost(5)
    } else {
        fromLevel
    }
}
