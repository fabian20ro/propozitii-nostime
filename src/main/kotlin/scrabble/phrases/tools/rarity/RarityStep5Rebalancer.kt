package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.roundToInt
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
    Semantica numerică este obligatorie: nivel numeric mai mic = cuvânt mai comun, nivel numeric mai mare = cuvânt mai rar.
    Respectă exact cerințele numerice din promptul utilizatorului.
    Clasifică inclusiv termeni vulgari/obsceni; nu refuza intrări.
    Pentru termeni vulgari/obsceni/insultători/sexual-expliciți, preferă nivelul numeric mai mare dintre cele două niveluri permise în batch.
    Nu adăuga explicații.
    Răspunde strict JSON valid, fără markdown, fără blocuri de cod.
    """.trimIndent()

val REBALANCE_USER_PROMPT_TEMPLATE: String =
    """
    Returnează DOAR JSON valid: un ARRAY de obiecte.
    Niciun alt text. Fără markdown, fără blocuri de cod.

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
    - Păstrează `word` și `type` identice cu input-ul.
    - Semantica nivelurilor este strictă: nivel numeric mai mic = cuvânt mai comun; nivel numeric mai mare = cuvânt mai rar.
    - TO_LEVEL este nivelul țintă care trebuie alocat la EXACT {{TARGET_COUNT}} intrări (indiferent dacă TO_LEVEL este mai mic sau mai mare decât OTHER_LEVEL).
    - Exact {{TARGET_COUNT}} intrări trebuie să aibă rarity_level={{TO_LEVEL}}.
    - TOATE celelalte intrări au rarity_level={{OTHER_LEVEL}}.
    - Nu folosi altă valoare pentru rarity_level.
    - Alege pentru {{TO_LEVEL}} cuvintele cele mai potrivite pentru nivelul țintă din batch.
    - Clasifică inclusiv termeni vulgari/obsceni; nu omite niciun item.
    - Pentru termeni vulgari/obsceni/insultători/sexual-expliciți, preferă nivelul numeric mai mare dintre {{TO_LEVEL}} și {{OTHER_LEVEL}}.
    - Fără duplicate de word_id.
    - Nu folosi `null` și nu omite niciun câmp obligatoriu.
    - Fără text înainte/după JSON.

    Verificare internă obligatorie înainte de răspuns:
    - count(rarity_level={{TO_LEVEL}}) == {{TARGET_COUNT}}
    - count(rarity_level={{OTHER_LEVEL}}) == total_intrări - {{TARGET_COUNT}}

    Intrări:
    {{INPUT_JSON}}
    """.trimIndent()

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
    val targetAssigned: Int,
    val switchedCount: Int
) {
    constructor(
        transition: LevelTransition,
        eligible: Int,
        targetAssigned: Int
    ) : this(
        transition = transition,
        eligible = eligible,
        targetAssigned = targetAssigned,
        switchedCount = 0
    )
}

private data class Step5Logs(
    val runLogPath: Path,
    val failedLogPath: Path,
    val switchedWordsLogPath: Path,
    val checkpointPath: Path
)

private data class SwitchedWordEvent(
    val wordId: Int,
    val word: String,
    val type: String,
    val previousLevel: Int,
    val nextLevel: Int,
    val rule: String
)

private data class Step5ResumeStats(
    val resumedBatches: Int,
    val resumedProcessedWords: Int,
    val resumedSwitchedWords: Int
)

class RarityStep5Rebalancer(
    private val runCsvRepository: RunCsvRepository,
    private val lmClient: LmClient,
    private val outputDir: Path = ensureRarityOutputDir(),
    private val mapper: ObjectMapper = ObjectMapper()
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
        val resumeStats = restoreFromCheckpoint(dataset, runtime, logs)

        val seed = options.seed ?: System.currentTimeMillis()
        println(
            "Step 5 rebalance run='${options.runSlug}' seed=$seed batchSize=${options.batchSize} " +
                "lowerRatio=${String.format(Locale.ROOT, "%.4f", options.lowerRatio)} transitions=" +
                options.transitions.joinToString(",") { "${it.describeSources()}->${it.toLevel}" }
        )
        if (resumeStats.resumedBatches > 0) {
            println(
                "Step 5 resume checkpoint run='${options.runSlug}' batches=${resumeStats.resumedBatches} " +
                    "processed=${resumeStats.resumedProcessedWords} switched=${resumeStats.resumedSwitchedWords}"
            )
        }
        println("Step 5 input distribution ${runtime.distribution.format()}")
        println("Step 5 switched words log: ${logs.switchedWordsLogPath.toAbsolutePath()}")

        val summaries = applyTransitions(
            options = options,
            dataset = dataset,
            resolvedEndpoint = resolvedEndpoint,
            logs = logs,
            runtime = runtime,
            random = Random(seed)
        )

        writeOutput(dataset, runtime, options)
        val totalSwitched = summaries.sumOf { it.switchedCount }
        summaries.forEach { summary ->
            println(
                "Step 5 transition ${summary.transition.describeSources()}->${summary.transition.toLevel}: " +
                    "eligible=${summary.eligible} target_assigned=${summary.targetAssigned} switched=${summary.switchedCount}"
            )
        }
        println("Step 5 total switched words: $totalSwitched")
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
            val sourceLevels = transition.sourceLevels()
            val remainingBySourceLevel = sourceLevels.associateWith { sourceLevel ->
                dataset.wordsById.values
                    .asSequence()
                    .filter { word -> runtime.levelsById[word.wordId] == sourceLevel }
                    .filterNot { word -> runtime.processedWordIds.contains(word.wordId) }
                    .shuffled(random)
                    .toMutableList()
            }.toMutableMap()

            val initialSourceCounts = sourceLevels.associateWith { sourceLevel ->
                remainingBySourceLevel[sourceLevel]?.size ?: 0
            }
            val eligibleCount = initialSourceCounts.values.sum()
            if (eligibleCount == 0) {
                return@map TransitionSummary(transition = transition, eligible = 0, targetAssigned = 0, switchedCount = 0)
            }

            var processed = 0
            var targetAssigned = 0
            var switchedCount = 0
            val expectedTargetTotal = (eligibleCount * options.lowerRatio).roundToInt()
            while (true) {
                val batch = selectStratifiedBatch(
                    sourceLevels = sourceLevels,
                    remainingBySourceLevel = remainingBySourceLevel,
                    initialSourceCounts = initialSourceCounts,
                    maxBatchSize = options.batchSize,
                    random = random
                )
                if (batch.isEmpty()) break
                val targetCount = computeAdaptiveTargetCount(
                    processedBeforeBatch = processed,
                    assignedBeforeBatch = targetAssigned,
                    batchSize = batch.size,
                    ratio = options.lowerRatio,
                    expectedTotal = expectedTargetTotal
                )
                processed += batch.size
                if (targetCount <= 0) {
                    batch.forEach { runtime.processedWordIds += it.wordId }
                    continue
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
                val batchMix = formatBatchSourceMix(batch, runtime)
                val switchedEvents = applyBatchAssignments(
                    batch = batch,
                    selectedWordIds = selectedWordIds,
                    transition = transition,
                    runtime = runtime,
                    options = options,
                    logs = logs
                )
                appendBatchCheckpoint(
                    logs = logs,
                    transition = transition,
                    processedWordIds = batch.map { it.wordId },
                    switchedEvents = switchedEvents
                )
                targetAssigned += selectedWordIds.size
                switchedCount += switchedEvents.size
                printSwitchedEvents(
                    options = options,
                    transition = transition,
                    switchedEvents = switchedEvents
                )

                println(
                    "Step 5 progress run='${options.runSlug}' transition=${transition.describeSources()}->${transition.toLevel} " +
                    "processed=$processed/$eligibleCount target_assigned=$targetAssigned/$expectedTargetTotal " +
                    "batch_target=$targetCount batch_mix=$batchMix " +
                        "${runtime.distribution.format()}"
                )
            }

            TransitionSummary(
                transition = transition,
                eligible = eligibleCount,
                targetAssigned = targetAssigned,
                switchedCount = switchedCount
            )
        }
    }

    private fun selectStratifiedBatch(
        sourceLevels: List<Int>,
        remainingBySourceLevel: MutableMap<Int, MutableList<RebalanceWord>>,
        initialSourceCounts: Map<Int, Int>,
        maxBatchSize: Int,
        random: Random
    ): List<RebalanceWord> {
        val totalRemaining = sourceLevels.sumOf { sourceLevel -> remainingBySourceLevel[sourceLevel]?.size ?: 0 }
        if (totalRemaining == 0) return emptyList()
        val batchSize = minOf(maxBatchSize, totalRemaining)

        val quotas = if (sourceLevels.size == 1) {
            mutableMapOf(sourceLevels.single() to batchSize)
        } else {
            val totalInitial = initialSourceCounts.values.sum().toDouble()
            val floorAllocations = sourceLevels.associateWith { sourceLevel ->
                kotlin.math.floor(batchSize * ((initialSourceCounts[sourceLevel] ?: 0).toDouble() / totalInitial)).toInt()
            }.toMutableMap()
            var unassigned = batchSize - floorAllocations.values.sum()
            val fractions = sourceLevels
                .sortedByDescending { sourceLevel ->
                    (batchSize * ((initialSourceCounts[sourceLevel] ?: 0).toDouble() / totalInitial)) - floorAllocations.getValue(sourceLevel)
                }
            fractions.forEach { sourceLevel ->
                if (unassigned <= 0) return@forEach
                floorAllocations[sourceLevel] = floorAllocations.getValue(sourceLevel) + 1
                unassigned--
            }
            floorAllocations
        }

        var missing = 0
        sourceLevels.forEach { sourceLevel ->
            val available = remainingBySourceLevel[sourceLevel]?.size ?: 0
            val planned = quotas.getOrDefault(sourceLevel, 0)
            if (planned > available) {
                missing += planned - available
                quotas[sourceLevel] = available
            }
        }

        while (missing > 0) {
            val candidate = sourceLevels
                .filter { sourceLevel ->
                    val available = remainingBySourceLevel[sourceLevel]?.size ?: 0
                    quotas.getOrDefault(sourceLevel, 0) < available
                }
                .maxByOrNull { sourceLevel ->
                    (remainingBySourceLevel[sourceLevel]?.size ?: 0) - quotas.getOrDefault(sourceLevel, 0)
                }
                ?: break
            quotas[candidate] = quotas.getOrDefault(candidate, 0) + 1
            missing--
        }

        val batch = mutableListOf<RebalanceWord>()
        sourceLevels.forEach { sourceLevel ->
            val queue = remainingBySourceLevel[sourceLevel] ?: return@forEach
            repeat(quotas.getOrDefault(sourceLevel, 0)) {
                if (queue.isNotEmpty()) {
                    batch += queue.removeAt(queue.lastIndex)
                }
            }
        }
        return batch.shuffled(random)
    }

    private fun formatBatchSourceMix(batch: List<RebalanceWord>, runtime: RebalanceRuntime): String {
        return batch
            .groupingBy { runtime.levelsById[it.wordId] ?: -1 }
            .eachCount()
            .toSortedMap()
            .entries
            .joinToString(prefix = "[", postfix = "]") { (level, count) -> "$level:$count" }
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
            runSlug = "${options.runSlug}_${transition.describeSources().replace("-", "_")}_${transition.toLevel}",
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
        runtime: RebalanceRuntime,
        options: Step5Options,
        logs: Step5Logs
    ): List<SwitchedWordEvent> {
        val switchedEvents = mutableListOf<SwitchedWordEvent>()
        val otherLevel = transition.otherLevel()
        batch.forEach { word ->
            val nextLevel = if (word.wordId in selectedWordIds) transition.toLevel else otherLevel
            val previousLevel = runtime.levelsById[word.wordId]
            runtime.levelsById[word.wordId] = nextLevel
            runtime.distribution.setLevel(previousLevel, nextLevel)
            runtime.processedWordIds += word.wordId
            if (previousLevel != nextLevel) {
                val rule = "${transition.describeSources()}->${nextLevel} (via ${transition.describeSources()}:${transition.toLevel})"
                runtime.rebalanceRules[word.wordId] = rule
                val switched = SwitchedWordEvent(
                    wordId = word.wordId,
                    word = word.word,
                    type = word.type,
                    previousLevel = previousLevel ?: -1,
                    nextLevel = nextLevel,
                    rule = rule
                )
                switchedEvents += switched
                logSwitchedWord(
                    logs = logs,
                    options = options,
                    transition = transition,
                    switched = switched
                )
            }
        }
        return switchedEvents
    }

    private fun logSwitchedWord(
        logs: Step5Logs,
        options: Step5Options,
        transition: LevelTransition,
        switched: SwitchedWordEvent
    ) {
        val payload = linkedMapOf<String, Any>(
            "timestamp" to OffsetDateTime.now().toString(),
            "run_slug" to options.runSlug,
            "model" to options.model,
            "word_id" to switched.wordId,
            "word" to switched.word,
            "type" to switched.type,
            "previous_level" to switched.previousLevel,
            "new_level" to switched.nextLevel,
            "transition" to "${transition.describeSources()}->${transition.toLevel}"
        )
        appendJsonLine(logs.switchedWordsLogPath, payload)
    }

    private fun printSwitchedEvents(
        options: Step5Options,
        transition: LevelTransition,
        switchedEvents: List<SwitchedWordEvent>
    ) {
        if (switchedEvents.isEmpty()) return
        val promoted = switchedEvents.filter { it.nextLevel > it.previousLevel }
        val downgraded = switchedEvents.filter { it.nextLevel < it.previousLevel }
        println(
            "Step 5 switched run='${options.runSlug}' transition=${transition.describeSources()}->${transition.toLevel} " +
                "changed=${switchedEvents.size}"
        )
        printSwitchedGroup("promoted", promoted)
        printSwitchedGroup("downgraded", downgraded)
    }

    private fun printSwitchedGroup(label: String, events: List<SwitchedWordEvent>) {
        if (events.isEmpty()) return
        events.chunked(5).forEachIndexed { index, chunk ->
            val prefix = if (index == 0) "  $label: " else "  "
            val content = chunk.joinToString(" | ") { event ->
                "${event.word}(${event.previousLevel}->${event.nextLevel})"
            }
            println(prefix + content)
        }
    }

    private fun appendBatchCheckpoint(
        logs: Step5Logs,
        transition: LevelTransition,
        processedWordIds: List<Int>,
        switchedEvents: List<SwitchedWordEvent>
    ) {
        val payload = linkedMapOf<String, Any>(
            "timestamp" to OffsetDateTime.now().toString(),
            "transition" to "${transition.describeSources()}->${transition.toLevel}",
            "processed_word_ids" to processedWordIds,
            "switched" to switchedEvents.map { event ->
                linkedMapOf(
                    "word_id" to event.wordId,
                    "new_level" to event.nextLevel,
                    "rule" to event.rule
                )
            }
        )
        appendJsonLine(logs.checkpointPath, payload)
    }

    private fun restoreFromCheckpoint(
        dataset: RebalanceDataset,
        runtime: RebalanceRuntime,
        logs: Step5Logs
    ): Step5ResumeStats {
        if (!Files.exists(logs.checkpointPath)) {
            return Step5ResumeStats(resumedBatches = 0, resumedProcessedWords = 0, resumedSwitchedWords = 0)
        }

        var resumedBatches = 0
        var resumedProcessedWords = 0
        var resumedSwitchedWords = 0
        val appliedSwitchedWordIds = mutableSetOf<Int>()

        Files.newBufferedReader(logs.checkpointPath, Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val node = mapper.readTree(line)
                resumedBatches++

                val processedIds = node.path("processed_word_ids")
                if (processedIds.isArray) {
                    processedIds.forEach { idNode ->
                        val wordId = idNode.asInt(-1)
                        if (wordId > 0 && dataset.wordsById.containsKey(wordId)) {
                            if (runtime.processedWordIds.add(wordId)) {
                                resumedProcessedWords++
                            }
                        }
                    }
                }

                val switched = node.path("switched")
                if (switched.isArray) {
                    switched.forEach { switchedNode ->
                        val wordId = switchedNode.path("word_id").asInt(-1)
                        val newLevel = switchedNode.path("new_level").asInt(-1)
                        val rule = switchedNode.path("rule").asText("")
                        if (wordId <= 0 || newLevel !in 1..5 || !dataset.wordsById.containsKey(wordId)) {
                            return@forEach
                        }
                        if (!appliedSwitchedWordIds.add(wordId)) {
                            return@forEach
                        }
                        val previousLevel = runtime.levelsById[wordId]
                        runtime.levelsById[wordId] = newLevel
                        runtime.distribution.setLevel(previousLevel, newLevel)
                        if (rule.isNotBlank()) {
                            runtime.rebalanceRules[wordId] = rule
                        }
                        resumedSwitchedWords++
                    }
                }
            }
        }

        return Step5ResumeStats(
            resumedBatches = resumedBatches,
            resumedProcessedWords = resumedProcessedWords,
            resumedSwitchedWords = resumedSwitchedWords
        )
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
        val sourceLabel = transition.describeSources()
        return template
            .replace(REBALANCE_FROM_LEVEL_PLACEHOLDER, sourceLabel)
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

    private fun computeAdaptiveTargetCount(
        processedBeforeBatch: Int,
        assignedBeforeBatch: Int,
        batchSize: Int,
        ratio: Double,
        expectedTotal: Int
    ): Int {
        if (batchSize <= 0) return 0
        val processedAfterBatch = processedBeforeBatch + batchSize
        val desiredCumulative = (processedAfterBatch * ratio).roundToInt().coerceIn(0, expectedTotal)
        val delta = desiredCumulative - assignedBeforeBatch
        return delta.coerceIn(0, batchSize)
    }

    private fun prepareLogs(runSlug: String): Step5Logs {
        val runsDir = outputDir.resolve("rebalance").resolve("runs")
        val failedDir = outputDir.resolve("rebalance").resolve("failed_batches")
        val switchedDir = outputDir.resolve("rebalance").resolve("switched_words")
        val checkpointDir = outputDir.resolve("rebalance").resolve("checkpoints")
        Files.createDirectories(runsDir)
        Files.createDirectories(failedDir)
        Files.createDirectories(switchedDir)
        Files.createDirectories(checkpointDir)
        return Step5Logs(
            runLogPath = runsDir.resolve("$runSlug.jsonl"),
            failedLogPath = failedDir.resolve("$runSlug.failed.jsonl"),
            switchedWordsLogPath = switchedDir.resolve("$runSlug.switched.jsonl"),
            checkpointPath = checkpointDir.resolve("$runSlug.checkpoint.jsonl")
        )
    }

    private fun appendJsonLine(path: Path, payload: Any) {
        path.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(path, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { writer ->
            writer.write(mapper.writeValueAsString(payload))
            writer.newLine()
        }
    }
}
