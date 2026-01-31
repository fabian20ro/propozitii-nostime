package scrabble.phrases.words

data class Adjective(
    override val word: String,
    override val syllables: Int = WordUtils.computeSyllableNumber(word),
    override val rhyme: String = WordUtils.computeRhyme(word),
    val feminine: String = computeFeminine(word)
) : Word {

    companion object {
        fun computeFeminine(word: String): String = when {
            word.endsWith("esc") -> word.substring(0, word.length - 2) + "ască"
            word.endsWith("eț") -> word.substring(0, word.length - 1) + "ață"
            word.endsWith("or") -> word.substring(0, word.length - 1) + "are"
            word.endsWith("os") -> word.substring(0, word.length - 1) + "asă"
            word.endsWith("iu") -> word.substring(0, word.length - 1) + "e"
            word.endsWith("ci") -> word.substring(0, word.length - 1) + "e"
            word.endsWith("ru") -> word.substring(0, word.length - 1) + "ă"
            word.endsWith("e") || word.endsWith("o") || word.endsWith("i") -> word
            else -> word + "ă"
        }
    }
}
