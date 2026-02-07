package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class HaikuProvider(
    private val repo: WordRepository,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val noun = repo.getRandomNounByArticulatedSyllables(5, maxRarity = maxRarity)
            ?: repo.getRandomNoun(maxRarity = maxRarity)

        // Adjective syllables: 3 for feminine nouns (feminine form +1 syllable), 4 for masculine
        val adjSyllables = if (noun.gender == NounGender.F) 3 else 4
        val adj = repo.getRandomAdjectiveBySyllables(adjSyllables, maxRarity = maxRarity)
            ?: throw IllegalStateException("No adjective with $adjSyllables syllables found")

        val adjForm = adj.forGender(noun.gender)

        // Verb with 3 syllables
        val verb = repo.getRandomVerbBySyllables(3, maxRarity = maxRarity)
            ?: throw IllegalStateException("No verb with 3 syllables found")

        // Second noun: articulated form has 5 syllables, distinct from first
        val noun2 = repo.getRandomNounByArticulatedSyllables(5, maxRarity = maxRarity, exclude = setOf(noun.word))
            ?: repo.getRandomNoun(maxRarity = maxRarity, exclude = setOf(noun.word))

        return "${noun.articulated} / $adjForm ${verb.word} / ${noun2.articulated}."
    }
}
