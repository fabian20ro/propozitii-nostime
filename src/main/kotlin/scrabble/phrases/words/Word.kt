package scrabble.phrases.words

sealed interface Word {
    val word: String
    val syllables: Int
    val rhyme: String
}

val Word.syllableCount: Int
    get() = this.syllables
