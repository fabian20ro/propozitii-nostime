package scrabble.phrases.tools.rarity

import scrabble.phrases.tools.rarity.lmstudio.LmStudioClient
import java.nio.file.Path
import java.nio.file.Paths

class RarityCli(
    private val wordStore: WordStore = JdbcWordStore(DbConnectionFactory()),
    private val runCsvRepository: RunCsvRepository = RunCsvRepository(),
    private val lmClient: LmClient = LmStudioClient(),
    private val lockManager: RunLockManager = RunLockManager(),
    private val outputDir: Path = ensureRarityOutputDir()
) {

    fun run(args: Array<String>) {
        val (step, rawArgs) = resolveStep(args)
        val options = parseArgs(rawArgs)

        when (step) {
            "step1" -> runStep1()
            "step2" -> runStep2(options)
            "step3" -> runStep3(options)
            "step4" -> runStep4(options)
            "step5" -> runStep5(options)
            else -> error("Unknown step '$step'. Use one of: step1, step2, step3, step4, step5.")
        }
    }

    private fun runStep1() {
        RarityStep1Exporter(
            wordStore = wordStore,
            runCsvRepository = runCsvRepository,
            outputDir = outputDir
        ).execute()
    }

    private fun runStep2(options: Map<String, String>) {
        val stepOptions = Step2Options(
            runSlug = sanitizeRunSlug(requiredOption(options, "run")),
            model = requiredOption(options, "model"),
            baseCsvPath = requiredPath(options, "base-csv"),
            outputCsvPath = requiredPath(options, "output-csv"),
            inputCsvPath = optionalPath(options, "input"),
            batchSize = intOption(options, "batch-size", DEFAULT_BATCH_SIZE, min = 1),
            limit = options["limit"]?.toIntOrNull()?.takeIf { it > 0 },
            maxRetries = intOption(options, "max-retries", DEFAULT_MAX_RETRIES, min = 1),
            timeoutSeconds = longOption(options, "timeout-seconds", DEFAULT_TIMEOUT_SECONDS, min = 5),
            maxTokens = intOption(options, "max-tokens", DEFAULT_MAX_TOKENS, min = 64),
            skipPreflight = booleanOption(options, "skip-preflight", default = false),
            force = booleanOption(options, "force", default = false),
            endpointOption = options["endpoint"] ?: System.getenv("LMSTUDIO_API_URL"),
            baseUrlOption = options["base-url"] ?: System.getenv("LMSTUDIO_BASE_URL"),
            systemPrompt = loadPrompt(options["system-prompt-file"], SYSTEM_PROMPT),
            userTemplate = loadPrompt(options["user-template-file"], USER_PROMPT_TEMPLATE)
        )

        RarityStep2Scorer(
            runCsvRepository = runCsvRepository,
            lmClient = lmClient,
            lockManager = lockManager,
            outputDir = outputDir
        ).execute(stepOptions)
    }

    private fun runStep3(options: Map<String, String>) {
        val stepOptions = Step3Options(
            runACsvPath = requiredPath(options, "run-a-csv"),
            runBCsvPath = requiredPath(options, "run-b-csv"),
            runCCsvPath = optionalPath(options, "run-c-csv"),
            outputCsvPath = requiredPath(options, "output-csv"),
            outliersCsvPath = optionalPath(options, "outliers-csv") ?: outputDir.resolve("step3_outliers.csv"),
            baseCsvPath = optionalPath(options, "base-csv") ?: outputDir.resolve("step1_words.csv"),
            outlierThreshold = intOption(options, "outlier-threshold", DEFAULT_OUTLIER_THRESHOLD, min = 1),
            confidenceThreshold = options["confidence-threshold"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
                ?: DEFAULT_CONFIDENCE_THRESHOLD,
            mergeStrategy = Step3MergeStrategy.from(options["merge-strategy"])
        )

        RarityStep3Comparator(runCsvRepository).execute(stepOptions)
    }

    private fun runStep4(options: Map<String, String>) {
        val stepOptions = Step4Options(
            finalCsvPath = requiredPath(options, "final-csv"),
            mode = UploadMode.from(options["mode"]),
            reportPath = optionalPath(options, "report-csv") ?: outputDir.resolve("step4_upload_report.csv"),
            uploadBatchId = options["upload-batch-id"]
        )

        RarityStep4Uploader(
            wordStore = wordStore,
            runCsvRepository = runCsvRepository,
            uploadMarkerWriter = UploadMarkerWriter(runCsvRepository)
        ).execute(stepOptions)
    }

    private fun runStep5(options: Map<String, String>) {
        val fromLevel = options["from-level"]?.toIntOrNull()
        val toLevel = options["to-level"]?.toIntOrNull()
        val transitions = when {
            fromLevel != null || toLevel != null -> {
                require(fromLevel != null && toLevel != null) {
                    "Step 5 requires both --from-level and --to-level when one is provided."
                }
                requireValidStep5Transition(fromLevel, toLevel)
                listOf(LevelTransition(fromLevel = fromLevel, toLevel = toLevel))
            }
            else -> parseStep5Transitions(options["transitions"])
        }
        validateTransitionSet(transitions)

        val inputCsv = options["step2-csv"] ?: options["input-csv"]
        require(!inputCsv.isNullOrBlank()) {
            "Missing required option --step2-csv (alias: --input-csv)"
        }

        val stepOptions = Step5Options(
            runSlug = sanitizeRunSlug(requiredOption(options, "run")),
            model = requiredOption(options, "model"),
            inputCsvPath = Paths.get(inputCsv),
            outputCsvPath = requiredPath(options, "output-csv"),
            batchSize = intOption(options, "batch-size", DEFAULT_REBALANCE_BATCH_SIZE, min = 3),
            lowerRatio = options["lower-ratio"]?.toDoubleOrNull()?.coerceIn(0.01, 0.49) ?: DEFAULT_REBALANCE_LOWER_RATIO,
            maxRetries = intOption(options, "max-retries", DEFAULT_MAX_RETRIES, min = 1),
            timeoutSeconds = longOption(options, "timeout-seconds", DEFAULT_TIMEOUT_SECONDS, min = 5),
            maxTokens = intOption(options, "max-tokens", DEFAULT_MAX_TOKENS, min = 64),
            skipPreflight = booleanOption(options, "skip-preflight", default = false),
            endpointOption = options["endpoint"] ?: System.getenv("LMSTUDIO_API_URL"),
            baseUrlOption = options["base-url"] ?: System.getenv("LMSTUDIO_BASE_URL"),
            seed = options["seed"]?.toLongOrNull(),
            transitions = transitions,
            systemPrompt = loadPrompt(options["system-prompt-file"], REBALANCE_SYSTEM_PROMPT),
            userTemplate = loadPrompt(options["user-template-file"], REBALANCE_USER_PROMPT_TEMPLATE)
        )

        RarityStep5Rebalancer(
            runCsvRepository = runCsvRepository,
            lmClient = lmClient,
            outputDir = outputDir
        ).execute(stepOptions)
    }

    private fun requiredPath(options: Map<String, String>, key: String): Path = Paths.get(requiredOption(options, key))

    private fun optionalPath(options: Map<String, String>, key: String): Path? = options[key]?.let(Paths::get)

    private fun intOption(options: Map<String, String>, key: String, default: Int, min: Int): Int {
        return options[key]?.toIntOrNull()?.coerceAtLeast(min) ?: default
    }

    private fun longOption(options: Map<String, String>, key: String, default: Long, min: Long): Long {
        return options[key]?.toLongOrNull()?.coerceAtLeast(min) ?: default
    }

    private fun booleanOption(options: Map<String, String>, key: String, default: Boolean): Boolean {
        return options[key]?.toBooleanStrictOrNull() ?: default
    }
}
