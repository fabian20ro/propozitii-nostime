package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender
import scrabble.phrases.words.WordUtils

class HaikuProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        // Find a noun whose articulated form has 5 syllables
        val noun = findNounWith5ArticulatedSyllables()

        // Adjective syllables: 3 for feminine nouns (feminine form +1 syllable), 4 for masculine
        val adjSyllables = if (noun.gender == NounGender.F) 3 else 4
        val adj = repo.getRandomAdjectiveBySyllables(adjSyllables)
            ?: throw IllegalStateException("No adjective with $adjSyllables syllables found")

        val adjForm = if (noun.gender == NounGender.F) adj.feminine else adj.word

        // Verb with 3 syllables
        val verb = repo.getRandomVerbBySyllables(3)
            ?: throw IllegalStateException("No verb with 3 syllables found")

        // Second noun: articulated form has 5 syllables (independent of first noun's rhyme)
        val noun2 = repo.getRandomNounByArticulatedSyllables(5)
            ?: repo.getRandomNoun()

        return "${noun.articulated} / $adjForm ${verb.word} / ${noun2.articulated}."
    }

    private fun findNounWith5ArticulatedSyllables(): scrabble.phrases.words.Noun {
        // Try up to 50 random nouns to find one with 5 articulated syllables
        repeat(50) {
            val noun = repo.getRandomNoun()
            if (WordUtils.computeSyllableNumber(noun.articulated) == 5) {
                return noun
            }
        }
        // Fallback: use direct query
        return repo.getRandomNounByArticulatedSyllables(5)
            ?: repo.getRandomNoun()
    }
}
