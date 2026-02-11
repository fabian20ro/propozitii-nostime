package scrabble.phrases

data class AllSentencesResponse(
    val haiku: String,
    val distih: String,
    val comparison: String,
    val definition: String,
    val tautogram: String,
    val mirror: String
)
