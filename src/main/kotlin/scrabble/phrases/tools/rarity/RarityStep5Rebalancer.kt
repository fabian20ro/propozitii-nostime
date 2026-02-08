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
    Ești un clasificator lexical strict pentru limba română.
    Task-ul tău este doar repartizarea unui batch între două niveluri fixe.
    Respectă exact cerințele numerice din promptul utilizatorului.
    Nu adăuga explicații, nu refuza intrări.
    Răspunde strict JSON valid.
    """.trimIndent()

val REBALANCE_USER_PROMPT_TEMPLATE: String =
    """
    Returnează DOAR JSON valid: un ARRAY de obiecte.
    Niciun alt text.

    Pentru fiecare intrare, output-ul trebuie să conțină EXACT aceste câmpuri:
    - word_id (int)
    - word (string)
    - type ("N" | "A" | "V")
    - rarity_level (int, permis DOAR {{TO_LEVEL}} sau {{OTHER_LEVEL}})
    - tag ("common|less_common|rare|technical|regional|archaic|uncertain")
    - confidence (număr 0.0..1.0)

    Reguli obligatorii:
    - Un rezultat pentru fiecare intrare (același număr total).
    - Păstrează ordinea intrărilor și word_id-urile identice.
    - Exact {{TARGET_COUNT}} intrări trebuie să aibă rarity_level={{TO_LEVEL}}.
    - TOATE celelalte intrări au rarity_level={{OTHER_LEVEL}}.
    - Nu folosi altă valoare pentru rarity_level.
    - Alege pentru {{TO_LEVEL}} cele mai comune/uzuale cuvinte din batch.
    - Fără duplicate de word_id.
    - Fără text înainte/după JSON.

    Verificare internă obligatorie înainte de răspuns:
    - count(rarity_level={{TO_LEVEL}}) == {{TARGET_COUNT}}
    - count(rarity_level={{OTHER_LEVEL}}) == total_intrări - {{TARGET_COUNT}}

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

private data class RebalanceDataset(
    val inputHeaders: List<String>,
    val mutableRows: List<MutableMap<String, String>>,
    val wordsById: Map<Int, RebalanceWord>,
    val levelsById: MutableMap<Int, Int>
)

private data class RebalanceRuntime(
    val levelsById: MutableMap<Int, Int>,
    val distribution: RarityDistribution,
    val rebalanceRules: MutableMap<Int, String>,
    val processedWordIds: MutableSet<Int>
)

private data class TransitionSummary(
    val transition: LevelTransition,
    val eligible: Int,
    val targetAssigned: Int
)

private data class Step5Logs(
    val runLogPath: Path,
    val failedLogPath: Path
)

class RarityStep5Rebalancer(
    private val runCsvRepository: RunCsvRepository,
    private val lmClient: LmClient,
    private val outputDir: Path = ensureRarityOutputDir()
) {

    fun execute(options: Step5Options) {
        validateTransitionSet(options.transitions)
        val dataset = loadDataset(options.inputCsvPath)
        val resolvedEndpoint = resolveEndpoint(options)
        val logs = prepareLogs(options.runSlug)
        val runtime = RebalanceRuntime(
            levelsById = dataset.levelsById,
            distribution = RarityDistribution.fromLevels(dataset.levelsById.values),
            rebalanceRules = mutableMapOf(),
            processedWordIds = mutableSetOf()
        )

        val seed = options.seed ?: System.currentTimeMillis()
        println(
            "Step 5 rebalance run='${options.runSlug}' seed=$seed batchSize=${options.batchSize} " +
                "lowerRatio=${String.format(Locale.ROOT, "%.4f", options.lowerRatio)} transitions=" +
                options.transitions.joinToString(",") { "${it.fromLevel}->${it.toLevel}" }
        )
        println("Step 5 input distribution ${runtime.distribution.format()}")

        val summaries = applyTransitions(
            options = options,
            dataset = dataset,
            resolvedEndpoint = resolvedEndpoint,
            logs = logs,
            runtime = runtime,
            random = Random(seed)
        )

        writeOutput(dataset, runtime, options)
        summaries.forEach { summary ->
            println(
                "Step 5 transition ${summary.transition.fromLevel}->${summary.transition.toLevel}: " +
                    "eligible=${summary.eligible} target_assigned=${summary.targetAssigned}"
            )
        }
        println("Step 5 output distribution ${runtime.distribution.format()}")
        println("Step 5 output CSV: ${options.outputCsvPath.toAbsolutePath()}")
    }

    private fun loadDataset(inputCsvPath: Path): RebalanceDataset {
        val table = runCsvRepository.readTable(inputCsvPath)
        val required = listOf("word_id", "word", "type")
        val missing = required.filterNot(table.headers::contains)
        require(missing.isEmpty()) {
            "CSV ${inputCsvPath.toAbsolutePath()} is missing required columns: ${missing.joinToString(", ")}"
        }

        val levelColumn = resolveLevelColumn(table.headers)
        val mutableRows = table.records.map { record ->
            table.headers.zip(record.values).toMap().toMutableMap()
        }

        val wordsById = linkedMapOf<Int, RebalanceWord>()
        val levelsById = mutableMapOf<Int, Int>()
        mutableRows.forEachIndexed { index, row ->
            val lineNumber = index + 2
            val wordId = row["word_id"]?.toIntOrNull()
                ?: throw CsvFormatException("Invalid word_id at ${inputCsvPath.toAbsolutePath()}:$lineNumber")
            val level = row[levelColumn]?.toIntOrNull()
                ?: throw CsvFormatException("Invalid $levelColumn at ${inputCsvPath.toAbsolutePath()}:$lineNumber")
            if (level !in 1..5) {
                throw CsvFormatException("$levelColumn out of range at ${inputCsvPath.toAbsolutePath()}:$lineNumber")
            }
            wordsById[wordId] = RebalanceWord(
                wordId = wordId,
                word = row["word"].orEmpty(),
                type = row["type"].orEmpty()
            )
            levelsById[wordId] = level
        }

        return RebalanceDataset(
            inputHeaders = table.headers,
            mutableRows = mutableRows,
            wordsById = wordsById,
            levelsById = levelsById
        )
    }

    private fun resolveEndpoint(options: Step5Options): ResolvedEndpoint {
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
        return resolvedEndpoint
    }

    private fun applyTransitions(
        options: Step5Options,
        dataset: RebalanceDataset,
        resolvedEndpoint: ResolvedEndpoint,
        logs: Step5Logs,
        runtime: RebalanceRuntime,
        random: Random
    ): List<TransitionSummary> {
        return options.transitions.map { transition ->
            val eligibleWords = dataset.wordsById.values
                .asSequence()
                .filter { word -> runtime.levelsById[word.wordId] == transition.fromLevel }
                .filterNot { word -> runtime.processedWordIds.contains(word.wordId) }
                .shuffled(random)
                .toList()

            if (eligibleWords.isEmpty()) {
                return@map TransitionSummary(transition = transition, eligible = 0, targetAssigned = 0)
            }

            var processed = 0
            var targetAssigned = 0
            eligibleWords.chunked(options.batchSize).forEach { batch ->
                processed += batch.size
                val targetCount = computeTargetCount(batch.size, options.lowerRatio)
                if (targetCount <= 0) {
                    batch.forEach { runtime.processedWordIds += it.wordId }
                    return@forEach
                }

                val scored = scoreTransitionBatch(
                    options = options,
                    resolvedEndpoint = resolvedEndpoint,
                    logs = logs,
                    transition = transition,
                    targetCount = targetCount,
                    batch = batch
                )
                val selectedWordIds = selectTargetWordIds(batch, scored, transition, targetCount)
                applyBatchAssignments(batch, selectedWordIds, transition, runtime)
                targetAssigned += selectedWordIds.size

                println(
                    "Step 5 progress run='${options.runSlug}' transition=${transition.fromLevel}->${transition.toLevel} " +
                    "processed=$processed/${eligibleWords.size} target_assigned=$targetAssigned " +
                        "${runtime.distribution.format()}"
                )
            }

            TransitionSummary(
                transition = transition,
                eligible = eligibleWords.size,
                targetAssigned = targetAssigned
            )
        }
    }

    private fun scoreTransitionBatch(
        options: Step5Options,
        resolvedEndpoint: ResolvedEndpoint,
        logs: Step5Logs,
        transition: LevelTransition,
        targetCount: Int,
        batch: List<RebalanceWord>
    ): List<ScoreResult> {
        val scoringContext = ScoringContext(
            runSlug = "${options.runSlug}_${transition.fromLevel}_${transition.toLevel}",
            model = options.model,
            endpoint = resolvedEndpoint.endpoint,
            maxRetries = options.maxRetries,
            timeoutSeconds = options.timeoutSeconds,
            runLogPath = logs.runLogPath,
            failedLogPath = logs.failedLogPath,
            systemPrompt = renderTemplate(options.systemPrompt, transition, targetCount),
            userTemplate = renderTemplate(options.userTemplate, transition, targetCount),
            flavor = resolvedEndpoint.flavor,
            maxTokens = options.maxTokens
        )

        val baseRows = batch.map { BaseWordRow(wordId = it.wordId, word = it.word, type = it.type) }
        return lmClient.scoreBatchResilient(baseRows, scoringContext)
    }

    private fun selectTargetWordIds(
        batch: List<RebalanceWord>,
        scored: List<ScoreResult>,
        transition: LevelTransition,
        targetCount: Int
    ): Set<Int> {
        val batchIds = batch.map { it.wordId }.toSet()
        val scoredById = scored.associateBy { it.wordId }

        val selected = scored
            .asSequence()
            .filter { it.rarityLevel == transition.toLevel }
            .map { it.wordId }
            .filter { it in batchIds }
            .distinct()
            .take(targetCount)
            .toMutableSet()

        if (selected.size >= targetCount) {
            return selected
        }

        val fallback = batch
            .asSequence()
            .map { it.wordId }
            .filterNot { it in selected }
            .sortedWith(compareBy<Int> { scoredById[it]?.confidence ?: 0.5 }.thenBy { it })
            .take(targetCount - selected.size)
            .toList()
        selected += fallback
        return selected
    }

    private fun applyBatchAssignments(
        batch: List<RebalanceWord>,
        selectedWordIds: Set<Int>,
        transition: LevelTransition,
        runtime: RebalanceRuntime
    ) {
        val otherLevel = transition.otherLevel()
        batch.forEach { word ->
            val nextLevel = if (word.wordId in selectedWordIds) transition.toLevel else otherLevel
            val previousLevel = runtime.levelsById[word.wordId]
            runtime.levelsById[word.wordId] = nextLevel
            runtime.distribution.setLevel(previousLevel, nextLevel)
            runtime.processedWordIds += word.wordId
            if (previousLevel != nextLevel) {
                runtime.rebalanceRules[word.wordId] =
                    "${transition.fromLevel}->${nextLevel} (via ${transition.fromLevel}:${transition.toLevel})"
            }
        }
    }

    private fun writeOutput(dataset: RebalanceDataset, runtime: RebalanceRuntime, options: Step5Options) {
        val outputHeaders = dataset.inputHeaders.toMutableList()
        if (!outputHeaders.contains("final_level")) outputHeaders += "final_level"
        if (!outputHeaders.contains("rebalance_rule")) outputHeaders += "rebalance_rule"
        if (!outputHeaders.contains("rebalance_model")) outputHeaders += "rebalance_model"
        if (!outputHeaders.contains("rebalance_run")) outputHeaders += "rebalance_run"
        if (!outputHeaders.contains("rebalanced_at")) outputHeaders += "rebalanced_at"

        val rebalancedAt = OffsetDateTime.now().toString()
        val outputRows = dataset.mutableRows.map { row ->
            val wordId = row["word_id"]?.toIntOrNull()
                ?: throw CsvFormatException("Invalid word_id in memory while writing output")
            val finalLevel = runtime.levelsById[wordId]
                ?: throw CsvFormatException("Missing level for word_id=$wordId while writing output")
            row["final_level"] = finalLevel.toString()
            row["rebalance_rule"] = runtime.rebalanceRules[wordId].orEmpty()
            row["rebalance_model"] = if (runtime.rebalanceRules.containsKey(wordId)) options.model else row["rebalance_model"].orEmpty()
            row["rebalance_run"] = if (runtime.rebalanceRules.containsKey(wordId)) options.runSlug else row["rebalance_run"].orEmpty()
            row["rebalanced_at"] = if (runtime.rebalanceRules.containsKey(wordId)) rebalancedAt else row["rebalanced_at"].orEmpty()
            outputHeaders.map { header -> row[header].orEmpty() }
        }
        runCsvRepository.writeTableAtomic(options.outputCsvPath, outputHeaders, outputRows)
    }

    private fun renderTemplate(template: String, transition: LevelTransition, targetCount: Int): String {
        return template
            .replace(REBALANCE_FROM_LEVEL_PLACEHOLDER, transition.fromLevel.toString())
            .replace(REBALANCE_TO_LEVEL_PLACEHOLDER, transition.toLevel.toString())
            .replace(REBALANCE_OTHER_LEVEL_PLACEHOLDER, transition.otherLevel().toString())
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

    private fun computeTargetCount(batchSize: Int, ratio: Double): Int {
        if (batchSize < 3) return 0
        val target = floor(batchSize * ratio).toInt().coerceAtLeast(1)
        return target.coerceAtMost(batchSize - 1)
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

fun parseStep5Transitions(raw: String?): List<LevelTransition> {
    val input = raw?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_REBALANCE_TRANSITIONS
    val parsed = input.split(",")
        .map { token ->
            val parts = token.trim().split(":")
            require(parts.size == 2) {
                "Invalid transition token '$token'. Expected format from:to (example: 2:1)"
            }
            val from = parts[0].trim().toIntOrNull()
                ?: error("Invalid transition source level in '$token'")
            val to = parts[1].trim().toIntOrNull()
                ?: error("Invalid transition target level in '$token'")
            requireValidStep5Transition(from, to)
            LevelTransition(fromLevel = from, toLevel = to)
        }
    validateTransitionSet(parsed)
    return parsed.distinct().sortedBy { it.fromLevel }
}

fun requireValidStep5Transition(fromLevel: Int, toLevel: Int) {
    val validRange = fromLevel in 1..5 && toLevel in 1..5
    val validRelation = toLevel == fromLevel - 1 || toLevel == fromLevel
    val invalidTopSelfSplit = fromLevel == 5 && toLevel == 5
    require(validRange && validRelation && !invalidTopSelfSplit) {
        "Invalid transition '$fromLevel:$toLevel'. Allowed: one-step downgrade (ex: 3:2) " +
            "or keep+promote split (ex: 2:2). 5:5 is not allowed."
    }
}

fun validateTransitionSet(transitions: List<LevelTransition>) {
    require(transitions.isNotEmpty()) { "Step 5 requires at least one transition." }
    val duplicateFromLevels = transitions.groupBy { it.fromLevel }
        .filterValues { it.size > 1 }
        .keys
    require(duplicateFromLevels.isEmpty()) {
        "Step 5 transitions must have unique from-level values. Duplicates: ${duplicateFromLevels.sorted().joinToString(",")}"
    }
}

private fun LevelTransition.otherLevel(): Int {
    return if (toLevel == fromLevel) (toLevel + 1).coerceAtMost(5) else fromLevel
}
