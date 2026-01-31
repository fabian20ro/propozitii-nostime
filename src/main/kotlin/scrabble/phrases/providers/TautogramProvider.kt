package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class TautogramProvider(private val repo: WordRepository) : ISentenceProvider {

    companion object {
        private const val LETTERS = "abcdefghilmnoprstuvz"
    }

    override fun getSentence(): String {
        // Find a letter that has nouns, adjectives, and verbs
        val shuffledLetters = LETTERS.toList().shuffled()
        val letter = shuffledLetters.firstOrNull { repo.hasWordsForLetter(it) }
            ?: throw IllegalStateException("No letter found with all word types available")

        val noun1 = repo.getRandomNounByFirstLetter(letter)
            ?: throw IllegalStateException("No noun found for letter '$letter'")
        val adj = repo.getRandomAdjectiveByFirstLetter(letter)
            ?: throw IllegalStateException("No adjective found for letter '$letter'")
        val verb = repo.getRandomVerbByFirstLetter(letter)
            ?: throw IllegalStateException("No verb found for letter '$letter'")
        val noun2 = repo.getRandomNounByFirstLetter(letter)
            ?: throw IllegalStateException("No second noun found for letter '$letter'")

        val adjForm = if (noun1.gender == NounGender.F) adj.feminine else adj.word
        return "${noun1.articulated} $adjForm ${verb.word} ${noun2.articulated}."
    }
}
