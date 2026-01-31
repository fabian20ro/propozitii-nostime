package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class ComparisonProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val noun1 = repo.getRandomNoun()
        val adj = repo.getRandomAdjective()
        val noun2 = repo.getRandomNoun()

        val adjForm = if (noun1.gender == NounGender.F) adj.feminine else adj.word
        return "${noun1.articulated} e mai $adjForm dec\u00e2t ${noun2.articulated}."
    }
}
