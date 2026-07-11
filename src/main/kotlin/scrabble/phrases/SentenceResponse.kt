package scrabble.phrases

class SentenceResponse(val sentence: String) {
    init { require(sentence.isNotBlank()) { "sentence cannot be blank" } }
}
