package scrabble.phrases.words

data class Noun(
    override val word: String,
    val gender: NounGender,
    override val syllables: Int = WordUtils.computeSyllableNumber(word),
    override val rhyme: String = WordUtils.computeRhyme(word),
    val articulated: String = computeArticulated(word, gender)
) : Word {
    init {
        require(!word.isBlank()) { "Noun word must not be blank or whitespace-only" }
        val validPattern = Regex("^[a-zA-Z\\u0103\\u0102\\u00E2\\u00CE\\u00EE\\u0218\\u0219\\u021A\\u021B]+$")
        require(validPattern.matches(word)) { "Noun word contains invalid characters: '$word' (allowed: a-z, A-Z, ă/Ă, â/Î/î, Ș/ș, Ţ/ţ)" }
    }

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
