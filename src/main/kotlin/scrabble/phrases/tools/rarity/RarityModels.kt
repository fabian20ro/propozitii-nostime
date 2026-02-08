package scrabble.phrases.tools.rarity

import java.nio.file.Path

const val DEFAULT_LMSTUDIO_BASE_URL: String = "http://127.0.0.1:1234"
const val OPENAI_CHAT_COMPLETIONS_PATH: String = "/v1/chat/completions"
const val OPENAI_MODELS_PATH: String = "/v1/models"
const val LMSTUDIO_CHAT_PATH: String = "/api/v1/chat"
const val LMSTUDIO_MODELS_PATH: String = "/api/v1/models"

const val DEFAULT_BATCH_SIZE: Int = 50
const val DEFAULT_MAX_RETRIES: Int = 3
const val DEFAULT_TIMEOUT_SECONDS: Long = 300L
const val DEFAULT_PREFLIGHT_TIMEOUT_SECONDS: Long = 5L
const val DEFAULT_MAX_TOKENS: Int = 8000
const val MODEL_CRASH_BACKOFF_MS: Long = 10_000L
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
    Pentru fiecare intrare estimezi raritatea pe scară 1..5 pentru un vorbitor român contemporan din România.

    Scară:
    1 = vocabular de bază absolut (copii până în clasa a 4-a, cursant română nivel începător)
    2 = vocabular uzual general (majoritatea populației îl folosește/des întâlnește)
    3 = vocabular mediu (cunoscut, dar nu de bază)
    4 = vocabular rar (specializat/regional/livresc)
    5 = foarte rar / arhaic / regional puternic / obscur

    Reguli:
    - Evaluează doar forma lexicală, fără context propozițional.
    - Țintă de calibrare (soft, pe rulări mari): aproximativ 2% nivel 1, 8% nivel 2, 20% nivel 3, 30% nivel 4, 40% nivel 5.
    - Pentru batch-uri de 50, folosește ca reper aproximativ: 1 cuvânt la nivel 1 și 4 cuvinte la nivel 2.
    - Nu forța cotele dacă batch-ul este evident tehnic/arhaic; cotele sunt orientative, nu reguli rigide.
    - Nivelurile 1 și 2:
      - 1 doar pentru vocabular de bază absolută (copii clasele primare, începători).
      - 2 pentru vocabular uzual general, înțeles/folosit de majoritatea populației adulte.
    - Dacă nu ai semnal clar de vocabular uzual general, evită 1/2.
    - Dacă există semnal moderat de uz general, preferă 2 în loc de 3.
    - Folosește 5 pentru termeni evident foarte rari/arhaici/obscuri.
    - Dacă ești indecis între 3 și 4, preferă 4.
    - Dacă ești indecis între 1 și 2, preferă 2.
    - Dacă ești indecis între 2 și 3, preferă 2.
    - Dacă ești indecis între 4 și 5, preferă 4.
    - Nu refuza: pentru orice intrare întoarce o estimare; dacă e incert, folosește tag `uncertain` și confidence mai mic.
    - Nu inventa câmpuri.
    - Răspunde strict JSON valid, fără text extra.
    """.trimIndent()

val USER_PROMPT_TEMPLATE: String =
    """
    Returnează DOAR JSON cu schema:
    {
      "results": [
        {
          "word_id": 1,
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
    - Păstrează word_id identic cu input-ul.
    - rarity_level trebuie să fie întreg 1..5.
    - confidence între 0.0 și 1.0.
    - Nu refuza intrări; dacă ești nesigur, folosește `tag="uncertain"` cu confidence mai mic.
    - 1/2 doar când există indicii de vocabular de bază/uzual general.
    - Țintă soft pe rulări mari: aproximativ 2% nivel 1 și 8% nivel 2.
    - Pentru batch-uri de 50, reper orientativ: ~1 nivel 1 și ~4 nivel 2.
    - Dacă nu există semnal clar de frecvență mare, preferă 3 sau 4.
    - Dacă ești la limită între 3 și 4, preferă 4.
    - Dacă ești la limită între 2 și 3, preferă 2.
    - Dacă ești la limită între 4 și 5, preferă 4.
    - Fără text înainte/după JSON.

    Intrări:
    {{INPUT_JSON}}
    """.trimIndent()
