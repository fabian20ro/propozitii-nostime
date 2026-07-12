package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class DefinitionProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        try {
            val defined = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity)
            val noun = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(defined.word))
            require(noun.gender == NounGender.M || noun.gender == NounGender.F) { "noun '${noun.word}' has invalid gender $noun.gender" }
            val adj = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity)
            val verb = repo.getRandomVerb(minRarity = minRarity, maxRarity = maxRarity)
            val object_ = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(defined.word, noun.word))

            return "${defined.word.uppercase()}: ${noun.articulated} ${adj.forGender(noun.gender)} care ${verb.word} ${object_.articulated}."
        } catch (e: IllegalStateException) {
            throw IllegalStateException(
                "DefinitionProvider needs at least one noun, adjective and verb in rarity range ${rarityDesc(minRarity, maxRarity)} — database may be empty or misconfigured", e
            )
        }
    }

    private fun rarityDesc(min: Int, max: Int): String =
        if (min <= 1) "<= $max" else "between $min and $max"
}
