package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class DefinitionProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val defined = repo.getRandomNoun()
        val noun = repo.getRandomNoun()
        val adj = repo.getRandomAdjective()
        val verb = repo.getRandomVerb()

        val adjForm = if (noun.gender == NounGender.F) adj.feminine else adj.word
        return "${defined.word.uppercase()}: ${noun.articulated} $adjForm care ${verb.word}."
    }
}
