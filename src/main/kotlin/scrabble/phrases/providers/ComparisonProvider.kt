package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class ComparisonProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val noun1 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity)
        val adj = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity)
        val noun2 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(noun1.word))

        return "${noun1.articulated} e mai ${adj.forGender(noun1.gender)} dec\u00e2t ${noun2.articulated}."
    }
}
