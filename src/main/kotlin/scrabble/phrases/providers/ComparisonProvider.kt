package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class ComparisonProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val noun1 = repo.getRandomNoun()
        val adj = repo.getRandomAdjective()
        val noun2 = repo.getRandomNoun(exclude = setOf(noun1.word))

        return "${noun1.articulated} e mai ${adj.forGender(noun1.gender)} dec\u00e2t ${noun2.articulated}."
    }
}
