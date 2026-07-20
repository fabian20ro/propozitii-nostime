package scrabble.phrases.providers

/** Sentence generator for decorative phrase types (haiku, distih, tautogram, mirror, comparison, definition).
 *  Implementations produce one sentence string. The verse-line delimiter `" / "` is part of the contract:
 *  [HtmlVerseBreaker] converts it to `<br/>`; mutating that literal breaks rendering in the frontend.
 */
/** Value used by [HtmlVerseBreaker] to convert verse lines into `<br/>`.
 *  Do not change without updating the frontend sanitizer and HtmlVerseBreaker. */
const val VERSE_DELIMITER = " / "

@Suppress("ClassName")
private object VerseDelimiterContractGuard {
    init {
        require(VERSE_DELIMITER == " / ") {
            "VERSE_DELIMITER contract violated — must equal ' / ' for HtmlVerseBreaker frontend rendering"
        }
    }
}

fun interface ISentenceProvider {
    fun getSentence(): String
}
