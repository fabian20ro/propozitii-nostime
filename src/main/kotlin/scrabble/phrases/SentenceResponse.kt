package scrabble.phrases

class SentenceResponse(val sentence: String) {

    init { require(sentence.isNotBlank()) { "sentence is blank" } }

    companion object {
        /** Factory with distinct failure signals for null vs. blank input. */
        @JvmStatic fun of(input: String?): SentenceResponse = when (input) {
            null -> throw IllegalArgumentException("sentence is null")
            else -> SentenceResponse(input)
        }
    }
}
