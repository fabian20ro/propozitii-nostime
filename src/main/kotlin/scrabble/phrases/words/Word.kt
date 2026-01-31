package scrabble.phrases.words

sealed interface Word {
    val word: String
    val syllables: Int
    val rhyme: String
}
