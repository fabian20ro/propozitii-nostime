package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class TautogramProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        // Pick a random two-letter prefix that has nouns, adjectives, and verbs
        val prefix = repo.getRandomPrefixWithAllTypes()
            ?: throw IllegalStateException("No two-letter prefix found with all word types available")

        val noun1 = repo.getRandomNounByPrefix(prefix)
            ?: throw IllegalStateException("No noun found for prefix '$prefix'")
        val adj = repo.getRandomAdjectiveByPrefix(prefix)
            ?: throw IllegalStateException("No adjective found for prefix '$prefix'")
        val verb = repo.getRandomVerbByPrefix(prefix)
            ?: throw IllegalStateException("No verb found for prefix '$prefix'")
        val noun2 = repo.getRandomNounByPrefix(prefix)
            ?: throw IllegalStateException("No second noun found for prefix '$prefix'")

        val adjForm = if (noun1.gender == NounGender.F) adj.feminine else adj.word
        return "${noun1.articulated} $adjForm ${verb.word} ${noun2.articulated}."
    }
}
