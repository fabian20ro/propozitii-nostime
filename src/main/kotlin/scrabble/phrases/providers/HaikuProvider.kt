package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class HaikuProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val noun = repo.getRandomNounByArticulatedSyllables(5)
            ?: repo.getRandomNoun()

        // Adjective syllables: 3 for feminine nouns (feminine form +1 syllable), 4 for masculine
        val adjSyllables = if (noun.gender == NounGender.F) 3 else 4
        val adj = repo.getRandomAdjectiveBySyllables(adjSyllables)
            ?: throw IllegalStateException("No adjective with $adjSyllables syllables found")

        val adjForm = adj.forGender(noun.gender)

        // Verb with 3 syllables
        val verb = repo.getRandomVerbBySyllables(3)
            ?: throw IllegalStateException("No verb with 3 syllables found")

        // Second noun: articulated form has 5 syllables, distinct from first
        val noun2 = repo.getRandomNounByArticulatedSyllables(5, exclude = setOf(noun.word))
            ?: repo.getRandomNoun(exclude = setOf(noun.word))

        return "${noun.articulated} / $adjForm ${verb.word} / ${noun2.articulated}."
    }
}
