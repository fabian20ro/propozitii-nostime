package scrabble.phrases.words

data class Noun(
    override val word: String,
    val gender: NounGender,
    override val syllables: Int = WordUtils.computeSyllableNumber(word),
    override val rhyme: String = WordUtils.computeRhyme(word),
    val articulated: String = computeArticulated(word, gender)
) : Word {

    companion object {
        fun computeArticulated(word: String, gender: NounGender): String = when (gender) {
            NounGender.M, NounGender.N -> articulateMasculine(word)
            NounGender.F -> articulateFeminine(word)
        }

        private fun articulateMasculine(word: String): String = when {
            word.endsWith("u") -> word + "l"
            word.endsWith("e") -> word + "le"
            word.endsWith("ă") -> word + "l"
            else -> word + "ul"
        }

        private fun articulateFeminine(word: String): String = when {
            word.length > 1 && (word.endsWith("ă") || word.endsWith("ie")) ->
                word.dropLast(1) + "a"
            word.endsWith("a") -> word + "ua"
            else -> word + "a"
        }
    }
}
