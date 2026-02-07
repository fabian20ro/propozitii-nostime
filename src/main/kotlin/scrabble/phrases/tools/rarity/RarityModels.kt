package scrabble.phrases.tools.rarity

import java.nio.file.Path

const val DEFAULT_LMSTUDIO_BASE_URL: String = "http://127.0.0.1:1234"
const val OPENAI_CHAT_COMPLETIONS_PATH: String = "/v1/chat/completions"
const val OPENAI_MODELS_PATH: String = "/v1/models"
const val LMSTUDIO_CHAT_PATH: String = "/api/v1/chat"
const val LMSTUDIO_MODELS_PATH: String = "/api/v1/models"

const val DEFAULT_BATCH_SIZE: Int = 20
const val DEFAULT_MAX_RETRIES: Int = 3
const val DEFAULT_TIMEOUT_SECONDS: Long = 300L
const val DEFAULT_PREFLIGHT_TIMEOUT_SECONDS: Long = 5L
const val DEFAULT_MAX_TOKENS: Int = 1400
const val DEFAULT_OUTLIER_THRESHOLD: Int = 2
const val DEFAULT_CONFIDENCE_THRESHOLD: Double = 0.55
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
    "median_level",
    "spread",
    "is_outlier",
    "reason",
    "final_level"
)
val OUTLIERS_CSV_HEADERS: List<String> = listOf(
    "word_id",
    "word",
    "type",
    "run_a_level",
    "run_b_level",
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
    val scores: List<ScoreResult>?,
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
    val medianLevel: Int,
    val spread: Int,
    val isOutlier: Boolean,
    val reason: String,
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

val SYSTEM_PROMPT: String =
    """
    Ești evaluator lexical pentru limba română.
    Evaluezi “raritatea” unui cuvânt pe scară 1..5 pentru un vorbitor român contemporan din România.

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

val USER_PROMPT_TEMPLATE: String =
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
    {{INPUT_JSON}}
    """.trimIndent()
