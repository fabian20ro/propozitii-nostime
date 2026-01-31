package scrabble.phrases

data class AllSentencesResponse(
    val haiku: String,
    val couplet: String,
    val comparison: String,
    val definition: String,
    val tautogram: String,
    val mirror: String
)
