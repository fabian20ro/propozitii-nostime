package scrabble.phrases.words

data class Adjective(
    override val word: String,
    override val syllables: Int = WordUtils.computeSyllableNumber(word),
    override val rhyme: String = WordUtils.computeRhyme(word),
    val feminine: String = computeFeminine(word),
    val feminineSyllables: Int = WordUtils.computeSyllableNumber(feminine)
) : Word {

    fun forGender(gender: NounGender): String =
        if (gender == NounGender.F) feminine else word

    companion object {
        fun computeFeminine(word: String): String = when {
            word.endsWith("esc") -> word.substring(0, word.length - 2) + "ască"
            word.endsWith("eț") -> word.substring(0, word.length - 1) + "ață"
            word.endsWith("tor") -> word.substring(0, word.length - 2) + "oare"
            word.endsWith("șor") -> word.substring(0, word.length - 2) + "oară"
            word.endsWith("ior") -> word.substring(0, word.length - 2) + "oară"
            word.endsWith("os") -> word.substring(0, word.length - 1) + "asă"
            word.endsWith("iu") -> word.substring(0, word.length - 1) + "e"
            word.endsWith("ci") -> word.substring(0, word.length - 1) + "e"
            // "negru" has an irregular stem vowel alternation (e→ea) that the generic
            // -ru rule can't handle; other -gru words don't exist in standard Romanian.
            word == "negru" -> "neagră"
            word.endsWith("ru") -> word.substring(0, word.length - 1) + "ă"
            word.endsWith("țel") || word.endsWith("șel") || word.endsWith("rel") ->
                word.substring(0, word.length - 2) + "ică"
            word.endsWith("e") || word.endsWith("o") || word.endsWith("i") -> word
            else -> word + "ă"
        }
    }
}
