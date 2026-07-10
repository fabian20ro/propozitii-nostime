package scrabble.phrases.words

data class Verb(
    override val word: String,
    override val syllables: Int = WordUtils.computeSyllableNumber(word),
    override val rhyme: String = WordUtils.computeRhyme(word)
) : Word {
    init {
        require(word.isNotBlank()) { "verb word must not be blank" }
    }
}
