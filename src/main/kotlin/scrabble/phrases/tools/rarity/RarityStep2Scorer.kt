package scrabble.phrases.tools.rarity

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.util.Locale

data class Step2Options(
    val runSlug: String,
    val model: String,
    val baseCsvPath: Path,
    val outputCsvPath: Path,
    val inputCsvPath: Path?,
    val batchSize: Int,
    val limit: Int?,
    val maxRetries: Int,
    val timeoutSeconds: Long,
    val maxTokens: Int,
    val skipPreflight: Boolean,
    val force: Boolean,
    val endpointOption: String?,
    val baseUrlOption: String?,
    val systemPrompt: String,
    val userTemplate: String
)

private data class Step2Files(
    val runLogPath: Path,
    val failedLogPath: Path,
    val statePath: Path
)

private data class Step2Context(
    val pending: List<BaseWordRow>,
    val existingRows: MutableMap<Int, RunCsvRow>,
    val baseline: RunBaseline
)

private data class Step2Counters(
    val scoredCount: Int,
    val failedCount: Int
)

internal fun formatRarityDistribution(rarityCounts: IntArray): String {
    val total = (1..5).sumOf { level -> rarityCounts.getOrElse(level) { 0 } }
    val parts = (1..5).joinToString(" ") { level ->
        val count = rarityCounts.getOrElse(level) { 0 }
        val percent = if (total > 0) (count * 100.0) / total.toDouble() else 0.0
        "$level:$count(${String.format(Locale.ROOT, "%.1f", percent)}%)"
    }
    return "distribution=[$parts]"
}

class RarityStep2Scorer(
    private val runCsvRepository: RunCsvRepository,
    private val lmClient: LmClient,
    private val lockManager: RunLockManager,
    private val mapper: ObjectMapper = ObjectMapper(),
    private val outputDir: Path = ensureRarityOutputDir(),
    private val metrics: Step2Metrics? = null
) {

    fun execute(options: Step2Options) {
        val files = prepareFiles(options.runSlug)

        lockManager.acquire(options.outputCsvPath).use {
            writeState(files.statePath, runningState(options))
            try {
                val context = buildContext(options)
                if (context.pending.isEmpty()) {
                    handleNoPending(options, files.statePath)
                    return
                }

                val resolvedEndpoint = resolveEndpoint(options)
                val counters = scorePendingBatches(options, context, files, resolvedEndpoint)
                val pendingAfterRun = counters.failedCount

                runCsvRepository.mergeAndRewriteAtomic(
                    path = options.outputCsvPath,
                    inMemoryRows = context.existingRows.values,
                    baseline = context.baseline
                )

                writeState(files.statePath, completedState(options, files, counters, pendingAfterRun))
                printSummary(options, files, counters, pendingAfterRun)
            } catch (e: Exception) {
                writeState(files.statePath, failedState(options.runSlug, e))
                throw e
            }
        }
    }

    private fun prepareFiles(runSlug: String): Step2Files {
        val runsDir = outputDir.resolve("runs")
        val failedDir = outputDir.resolve("failed_batches")
        Files.createDirectories(runsDir)
        Files.createDirectories(failedDir)

        return Step2Files(
            runLogPath = runsDir.resolve("$runSlug.jsonl"),
            failedLogPath = failedDir.resolve("$runSlug.failed.jsonl"),
            statePath = runsDir.resolve("$runSlug.state.json")
        )
    }

    private fun runningState(options: Step2Options): Map<String, Any?> {
        return mapOf(
            "status" to "running",
            "run_slug" to options.runSlug,
            "model" to options.model,
            "pid" to ProcessHandle.current().pid(),
            "host" to resolveHost(),
            "started_at" to OffsetDateTime.now().toString(),
            "base_csv" to options.baseCsvPath.toAbsolutePath().toString(),
            "input_csv" to options.inputCsvPath?.toAbsolutePath()?.toString(),
            "output_csv" to options.outputCsvPath.toAbsolutePath().toString()
        )
    }

    private fun completedState(
        options: Step2Options,
        files: Step2Files,
        counters: Step2Counters,
        pendingCount: Int
    ): Map<String, Any?> {
        return mapOf(
            "status" to "completed",
            "run_slug" to options.runSlug,
            "finished_at" to OffsetDateTime.now().toString(),
            "scored" to counters.scoredCount,
            "failed" to counters.failedCount,
            "pending" to pendingCount,
            "output_csv" to options.outputCsvPath.toAbsolutePath().toString(),
            "run_log" to files.runLogPath.toAbsolutePath().toString(),
            "failed_log" to files.failedLogPath.toAbsolutePath().toString()
        )
    }

    private fun failedState(runSlug: String, error: Exception): Map<String, Any?> {
        return mapOf(
            "status" to "failed",
            "run_slug" to runSlug,
            "failed_at" to OffsetDateTime.now().toString(),
            "error" to (error.message ?: error::class.simpleName)
        )
    }

    private fun buildContext(options: Step2Options): Step2Context {
        val sourceCsv = options.inputCsvPath ?: options.baseCsvPath
        val baseRows = runCsvRepository.loadBaseRows(sourceCsv)
            .associateBy { it.wordId }
            .values
            .sortedBy { it.wordId }

        val existingRows = runCsvRepository.loadRunRows(options.outputCsvPath)
            .associateBy { it.wordId }
            .toMutableMap()

        val pending = baseRows
            .asSequence()
            .filter { options.force || existingRows[it.wordId] == null }
            .let { seq -> options.limit?.let { seq.take(it) } ?: seq }
            .toList()

        return Step2Context(
            pending = pending,
            existingRows = existingRows,
            baseline = runCsvRepository.computeBaseline(existingRows.values)
        )
    }

    private fun handleNoPending(options: Step2Options, statePath: Path) {
        writeState(
            statePath,
            mapOf(
                "status" to "completed",
                "run_slug" to options.runSlug,
                "finished_at" to OffsetDateTime.now().toString(),
                "scored" to 0,
                "failed" to 0,
                "pending" to 0,
                "message" to "No pending words"
            )
        )

        println("Step 2 complete. No pending words for run '${options.runSlug}'.")
        println("Run CSV: ${options.outputCsvPath.toAbsolutePath()}")
    }

    private fun resolveEndpoint(options: Step2Options): ResolvedEndpoint {
        val resolvedEndpoint = lmClient.resolveEndpoint(options.endpointOption, options.baseUrlOption)
        println(
            "LMStudio endpoint: ${resolvedEndpoint.endpoint} " +
                "(flavor=${resolvedEndpoint.flavor}, source=${resolvedEndpoint.source})"
        )

        if (options.skipPreflight) {
            println("Skipping LMStudio preflight (--skip-preflight=true)")
        } else {
            lmClient.preflight(resolvedEndpoint, options.model)
        }

        return resolvedEndpoint
    }

    private fun scorePendingBatches(
        options: Step2Options,
        context: Step2Context,
        files: Step2Files,
        resolvedEndpoint: ResolvedEndpoint
    ): Step2Counters {
        val totalPending = context.pending.size
        var scoredCount = 0
        var failedCount = 0
        var processed = 0
        val rarityCounts = IntArray(6)
        context.existingRows.values.forEach { row ->
            if (row.rarityLevel in 1..5) {
                rarityCounts[row.rarityLevel] += 1
            }
        }
        val minAdaptiveSize = (options.batchSize / 5)
            .coerceAtLeast(5)
            .coerceAtMost(options.batchSize)
        val adapter = BatchSizeAdapter(
            initialSize = options.batchSize,
            minSize = minAdaptiveSize
        )

        val scoringContext = ScoringContext(
            runSlug = options.runSlug,
            model = options.model,
            endpoint = resolvedEndpoint.endpoint,
            maxRetries = options.maxRetries,
            timeoutSeconds = options.timeoutSeconds,
            runLogPath = files.runLogPath,
            failedLogPath = files.failedLogPath,
            systemPrompt = options.systemPrompt,
            userTemplate = options.userTemplate,
            flavor = resolvedEndpoint.flavor,
            maxTokens = options.maxTokens
        )

        val remaining = context.pending.toMutableList()

        while (remaining.isNotEmpty()) {
            val batchSize = adapter.recommendedSize().coerceAtMost(remaining.size)
            val batch = remaining.subList(0, batchSize).toList()
            remaining.subList(0, batchSize).clear()

            val scored = lmClient.scoreBatchResilient(
                batch = batch,
                context = scoringContext
            )

            adapter.recordOutcome(scored.size.toDouble() / batch.size.toDouble())
            metrics?.recordBatchResult(batch.size, scored.size)

            if (scored.isNotEmpty()) {
                val rowsToAppend = toRunRows(scored, options.model, options.runSlug)
                rowsToAppend.forEach { context.existingRows[it.wordId] = it }
                runCsvRepository.appendRunRows(options.outputCsvPath, rowsToAppend)
                rowsToAppend.forEach { row ->
                    if (row.rarityLevel in 1..5) {
                        rarityCounts[row.rarityLevel] += 1
                    }
                }
                scoredCount += rowsToAppend.size
            }

            failedCount += (batch.size - scored.size)
            processed += batch.size
            printBatchProgress(
                runSlug = options.runSlug,
                processed = processed,
                totalPending = totalPending,
                scored = scoredCount,
                failed = failedCount,
                effectiveBatchSize = adapter.recommendedSize(),
                distribution = formatRarityDistribution(rarityCounts)
            )
        }

        return Step2Counters(scoredCount = scoredCount, failedCount = failedCount)
    }

    private fun toRunRows(scored: List<ScoreResult>, model: String, runSlug: String): List<RunCsvRow> {
        val scoredAt = OffsetDateTime.now().toString()
        return scored.map {
            RunCsvRow(
                wordId = it.wordId,
                word = it.word,
                type = it.type,
                rarityLevel = it.rarityLevel,
                tag = it.tag,
                confidence = it.confidence,
                scoredAt = scoredAt,
                model = model,
                runSlug = runSlug
            )
        }
    }

    private fun printBatchProgress(
        runSlug: String,
        processed: Int,
        totalPending: Int,
        scored: Int,
        failed: Int,
        effectiveBatchSize: Int,
        distribution: String
    ) {
        val remaining = (totalPending - processed).coerceAtLeast(0)
        val metricsLine = metrics?.formatProgress(remaining, effectiveBatchSize)
        if (metricsLine != null) {
            println("Step 2 progress run='$runSlug' $metricsLine $distribution")
        } else {
            println(
                "Step 2 progress run='$runSlug' processed=$processed/$totalPending " +
                    "scored=$scored failed=$failed remaining=$remaining $distribution"
            )
        }
    }

    private fun printSummary(options: Step2Options, files: Step2Files, counters: Step2Counters, pendingCount: Int) {
        val metricsSummary = metrics?.formatSummary()
        if (metricsSummary != null) {
            println(metricsSummary)
        } else {
            println(
                "Step 2 complete for run '${options.runSlug}': " +
                    "scored=${counters.scoredCount} failed=${counters.failedCount} pending=$pendingCount"
            )
        }
        println("Run CSV: ${options.outputCsvPath.toAbsolutePath()}")
        println("Run log: ${files.runLogPath.toAbsolutePath()}")
        println("Failed log: ${files.failedLogPath.toAbsolutePath()}")
    }

    private fun resolveHost(): String {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun writeState(path: Path, payload: Map<String, Any?>) {
        path.parent?.let { Files.createDirectories(it) }
        val json = mapper.writeValueAsString(payload)
        Files.writeString(
            path,
            "$json\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
