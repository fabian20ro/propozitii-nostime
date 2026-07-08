package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class ComparisonProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val noun1 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity) ?: return "Ceva e mai mare decât altceva."
        val adj = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity) ?: return "Ceva e mai mare decât altceva."
        val noun2 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(noun1.word)) ?: return "Ceva e mai mare decât altceva."

        return "${noun1.articulated} e mai ${adj.forGender(noun1.gender)} decâe ${noun2.articulated}."
    }
}
