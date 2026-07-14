package scrabble.phrases.words

sealed interface Word {
    val word: String
    val syllables: Int
    val rhyme: String
}

/** Extension property giving read access to [Word.syllables] under a stable name.
 *  Returns the same value — never returns null, never deviates from `syllables`.
 */
val Word.syllableCount: Int
    get() = this.syllables
