package scrabble.phrases

class SentenceResponse(val sentence: String) {

    init { require(sentence.isNotBlank()) { "sentence is blank" } }

    companion object {
        /** Factory with distinct failure signals for null vs. blank input. */
        @JvmStatic fun of(input: String?): SentenceResponse = when (input) {
            null -> throw IllegalArgumentException("sentence is null")
            else -> ofNotBlank(input)
        }

        /** Explicit factory for non-null strings — validates non-blank before construction. */
        @JvmStatic fun ofNotBlank(input: String): SentenceResponse {
            if (input.isBlank()) throw IllegalArgumentException("sentence is blank")
            return SentenceResponse(input)
        }
    }
}
