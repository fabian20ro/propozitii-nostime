package scrabble.phrases.tools.rarity

import kotlin.math.abs
import kotlin.math.min

/**
 * Fuzzy word matching for Romanian text where the LM may misspell diacritics
 * or make minor character errors.
 *
 * Romanian has two sets of characters that LMs frequently confuse:
 * - Comma-below: ș (U+0219), ț (U+021B) -- standard Romanian
 * - Cedilla:     ş (U+015F), ţ (U+0163) -- legacy encoding
 * - Breve/circumflex: ă (U+0103), â (U+00E2), î (U+00EE)
 *
 * The matcher first normalizes diacritics to ASCII, then allows up to
 * [MAX_EDIT_DISTANCE] edits for near-misses (e.g. character deletion/insertion).
 */
object FuzzyWordMatcher {

    private const val MAX_EDIT_DISTANCE = 2

    private val DIACRITICS_MAP = mapOf(
        'ă' to 'a', 'Ă' to 'A',
        'â' to 'a', 'Â' to 'A',
        'î' to 'i', 'Î' to 'I',
        'ș' to 's', 'Ș' to 'S',  // comma-below
        'ț' to 't', 'Ț' to 'T',  // comma-below
        'ş' to 's', 'Ş' to 'S',  // cedilla
        'ţ' to 't', 'Ţ' to 'T'   // cedilla
    )

    fun matches(expected: String, actual: String): Boolean {
        if (expected == actual) return true
        val normExpected = normalize(expected)
        val normActual = normalize(actual)
        if (normExpected == normActual) return true
        if (abs(normExpected.length - normActual.length) > MAX_EDIT_DISTANCE) return false
        return levenshtein(normExpected, normActual) <= MAX_EDIT_DISTANCE
    }

    internal fun normalize(text: String): String {
        return buildString(text.length) {
            for (ch in text) {
                append(DIACRITICS_MAP[ch] ?: ch)
            }
        }.lowercase()
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }

        return prev[b.length]
    }
}
