package scrabble.phrases.tools.rarity

import java.nio.file.Path

const val DEFAULT_LMSTUDIO_BASE_URL: String = "http://127.0.0.1:1234"
const val OPENAI_CHAT_COMPLETIONS_PATH: String = "/v1/chat/completions"
const val OPENAI_MODELS_PATH: String = "/v1/models"
const val LMSTUDIO_CHAT_PATH: String = "/api/v1/chat"
const val LMSTUDIO_MODELS_PATH: String = "/api/v1/models"
const val MODEL_GPT_OSS_20B: String = "openai/gpt-oss-20b"
const val MODEL_GLM_47_FLASH: String = "zai-org/glm-4.7-flash"
const val MODEL_MINISTRAL_3_8B: String = "ministral-3-8b-instruct-2512-mixed-8-6-bit"
const val MODEL_EUROLLM_22B_MLX_4BIT: String = "mlx-community/EuroLLM-22B-Instruct-2512-mlx-4bit"
const val MODEL_EUROLLM_22B: String = "eurollm-22b-instruct-2512-mlx"

const val DEFAULT_BATCH_SIZE: Int = 50
const val DEFAULT_MAX_RETRIES: Int = 3
const val DEFAULT_TIMEOUT_SECONDS: Long = 300L
const val DEFAULT_PREFLIGHT_TIMEOUT_SECONDS: Long = 5L
const val DEFAULT_MAX_TOKENS: Int = 8000
const val MODEL_CRASH_BACKOFF_MS: Long = 10_000L
const val DEFAULT_OUTLIER_THRESHOLD: Int = 2
const val DEFAULT_CONFIDENCE_THRESHOLD: Double = 0.55
val DEFAULT_STEP3_MERGE_STRATEGY: Step3MergeStrategy = Step3MergeStrategy.MEDIAN
const val FALLBACK_RARITY_LEVEL: Int = 4
const val USER_INPUT_PLACEHOLDER: String = "{{INPUT_JSON}}"

val BASE_CSV_HEADERS: List<String> = listOf("word_id", "word", "type")
val RUN_CSV_HEADERS: List<String> = listOf(
    "word_id",
    "word",
    "type",
    "rarity_level",
    "tag",
    "confidence",
    "scored_at",
    "model",
    "run_slug"
)
val COMPARISON_CSV_HEADERS: List<String> = listOf(
    "word_id",
    "word",
    "type",
    "run_a_level",
    "run_a_confidence",
    "run_b_level",
    "run_b_confidence",
    "run_c_level",
    "run_c_confidence",
    "median_level",
    "spread",
    "is_outlier",
    "reason",
    "merge_strategy",
    "merge_rule",
    "final_level"
)
val OUTLIERS_CSV_HEADERS: List<String> = listOf(
    "word_id",
    "word",
    "type",
    "run_a_level",
    "run_b_level",
    "run_c_level",
    "spread",
    "reason"
)
val UPLOAD_REPORT_HEADERS: List<String> = listOf("word_id", "previous_level", "new_level", "source")

val UPLOAD_MARKER_HEADERS: List<String> = listOf(
    "uploaded_at",
    "uploaded_level",
    "upload_status",
    "upload_batch_id"
)

enum class LmApiFlavor {
    OPENAI_COMPAT,
    LMSTUDIO_REST
}

enum class UploadMode {
    PARTIAL,
    FULL_FALLBACK;

    companion object {
        fun from(value: String?): UploadMode {
            return when (value?.trim()?.lowercase()) {
                null, "", "partial" -> PARTIAL
                "full-fallback", "full_fallback" -> FULL_FALLBACK
                else -> error("Invalid --mode '$value'. Use one of: partial, full-fallback")
            }
        }
    }
}

enum class Step3MergeStrategy {
    MEDIAN,
    ANY_EXTREMES;

    companion object {
        fun from(value: String?): Step3MergeStrategy {
            return when (value?.trim()?.lowercase()) {
                null, "", "median" -> MEDIAN
                "any-extremes", "any_extremes", "three-any-extremes", "three_any_extremes" -> ANY_EXTREMES
                else -> error("Invalid --merge-strategy '$value'. Use one of: median, any-extremes")
            }
        }
    }
}

data class BaseWordRow(
    val wordId: Int,
    val word: String,
    val type: String
)

data class RunCsvRow(
    val wordId: Int,
    val word: String,
    val type: String,
    val rarityLevel: Int,
    val tag: String,
    val confidence: Double,
    val scoredAt: String,
    val model: String,
    val runSlug: String
)

data class ScoreResult(
    val wordId: Int,
    val word: String,
    val type: String,
    val rarityLevel: Int,
    val tag: String,
    val confidence: Double
)

data class ResolvedEndpoint(
    val endpoint: String,
    val modelsEndpoint: String?,
    val flavor: LmApiFlavor,
    val source: String
)

data class BatchAttempt(
    val scores: List<ScoreResult>,
    val unresolved: List<BaseWordRow>,
    val lastError: String?,
    val connectivityFailure: Boolean
)

data class ComparisonRow(
    val wordId: Int,
    val word: String,
    val type: String,
    val runALevel: Int?,
    val runAConfidence: Double?,
    val runBLevel: Int?,
    val runBConfidence: Double?,
    val runCLevel: Int?,
    val runCConfidence: Double?,
    val medianLevel: Int,
    val spread: Int,
    val isOutlier: Boolean,
    val reason: String,
    val mergeStrategy: Step3MergeStrategy,
    val mergeRule: String,
    val finalLevel: Int
)

data class UploadReportRow(
    val wordId: Int,
    val previousLevel: Int,
    val newLevel: Int,
    val source: String
)

data class WordLevel(
    val wordId: Int,
    val rarityLevel: Int
)

data class UploadMarkerResult(
    val markerPath: Path,
    val usedCompanionFile: Boolean,
    val markedRows: Int
)

// Prompts are loaded from `docs/rarity-prompts/*.txt` via RarityCli defaults.
