package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class HaikuProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val noun = repo.getRandomNounByArticulatedSyllables(5, minRarity = minRarity, maxRarity = maxRarity)
            ?: repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity)

        // Adjective form in line 2 must be 4 syllables (+ verb 3 = 7 total).
        // For feminine nouns the feminine form is used, so query by feminine_syllables.
        // Falls back to masculine-syllable query if feminine_syllables column is not yet backfilled.
        val adj = if (noun.gender == NounGender.F) {
            repo.getRandomAdjectiveByFeminineSyllables(4, minRarity = minRarity, maxRarity = maxRarity)
                ?: repo.getRandomAdjectiveBySyllables(3, minRarity = minRarity, maxRarity = maxRarity)
        } else {
            repo.getRandomAdjectiveBySyllables(4, minRarity = minRarity, maxRarity = maxRarity)
        } ?: throw IllegalStateException("No adjective with 4 syllables found for gender ${noun.gender}")

        val adjForm = adj.forGender(noun.gender)

        // Verb with 3 syllables
        val verb = repo.getRandomVerbBySyllables(3, minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("No verb with 3 syllables found")

        // Second noun: articulated form has 5 syllables, distinct from first
        val noun2 = repo.getRandomNounByArticulatedSyllables(5, minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(noun.word))
            ?: repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(noun.word))

        return "${noun.articulated} / $adjForm ${verb.word} / ${noun2.articulated}."
    }
}
