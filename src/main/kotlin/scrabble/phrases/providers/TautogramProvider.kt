package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class TautogramProvider(
    private val repo: WordRepository,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        // Pick a random two-letter prefix that has nouns, adjectives, and verbs
        val prefix = repo.getRandomPrefixWithAllTypes(maxRarity = maxRarity)
            ?: throw IllegalStateException("No two-letter prefix found with all word types available")

        val noun1 = repo.getRandomNounByPrefix(prefix, maxRarity = maxRarity)
            ?: throw IllegalStateException("No noun found for prefix '$prefix'")
        val adj = repo.getRandomAdjectiveByPrefix(prefix, maxRarity = maxRarity)
            ?: throw IllegalStateException("No adjective found for prefix '$prefix'")
        val verb = repo.getRandomVerbByPrefix(prefix, maxRarity = maxRarity)
            ?: throw IllegalStateException("No verb found for prefix '$prefix'")
        val noun2 = repo.getRandomNounByPrefix(prefix, maxRarity = maxRarity, exclude = setOf(noun1.word))
            ?: throw IllegalStateException("No second noun found for prefix '$prefix'")

        return "${noun1.articulated} ${adj.forGender(noun1.gender)} ${verb.word} ${noun2.articulated}."
    }
}
