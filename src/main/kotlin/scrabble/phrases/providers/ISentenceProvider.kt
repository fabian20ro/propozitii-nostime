package scrabble.phrases.providers

/** Sentence generator for decorative phrase types (haiku, distih, tautogram, mirror, comparison, definition).
 *  Implementations produce one sentence string. The verse-line delimiter `" / "` is part of the contract:
 *  [HtmlVerseBreaker] converts it to `<br/>`; mutating that literal breaks rendering in the frontend.
 */
fun interface ISentenceProvider {
    fun getSentence(): String
}
