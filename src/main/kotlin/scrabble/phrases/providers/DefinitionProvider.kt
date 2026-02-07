package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class DefinitionProvider(
    private val repo: WordRepository,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val defined = repo.getRandomNoun(maxRarity = maxRarity)
        val noun = repo.getRandomNoun(maxRarity = maxRarity, exclude = setOf(defined.word))
        val adj = repo.getRandomAdjective(maxRarity = maxRarity)
        val verb = repo.getRandomVerb(maxRarity = maxRarity)
        val object_ = repo.getRandomNoun(maxRarity = maxRarity, exclude = setOf(defined.word, noun.word))

        return "${defined.word.uppercase()}: ${noun.articulated} ${adj.forGender(noun.gender)} care ${verb.word} ${object_.articulated}."
    }
}
