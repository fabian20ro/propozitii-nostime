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
            word == "negru" -> "neagră"
            word == "roșu" -> "roșie"
            word == "sec" -> "seacă"
            word == "des" -> "deasă"
            word == "drept" -> "dreaptă"
            word == "întreg" -> "întreagă"
            word == "deșert" -> "deșeartă"
            word == "mort" -> "moartă"
            word.endsWith("esc") -> word.dropLast(2) + "ască"
            word.endsWith("eț") -> word.dropLast(1) + "ață"
            word.endsWith("tor") -> word.dropLast(2) + "oare"
            word.endsWith("șor") -> word.dropLast(2) + "oară"
            word.endsWith("ior") -> word.dropLast(2) + "oară"
            word.endsWith("os") -> word.dropLast(1) + "asă"
            word.endsWith("iu") -> word.dropLast(1) + "e"
            word.endsWith("ci") -> word.dropLast(1) + "e"
            word.endsWith("ru") -> word.dropLast(1) + "ă"
            word.endsWith("țel") || word.endsWith("șel") || word.endsWith("rel") ->
                word.dropLast(2) + "ică"
            word.endsWith("e") || word.endsWith("o") || word.endsWith("i") -> word
            else -> word + "ă"
        }
    }
}
