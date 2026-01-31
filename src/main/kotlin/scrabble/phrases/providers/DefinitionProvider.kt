package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class DefinitionProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val defined = repo.getRandomNoun()
        val noun = repo.getRandomNoun(exclude = setOf(defined.word))
        val adj = repo.getRandomAdjective()
        val verb = repo.getRandomVerb()
        val object_ = repo.getRandomNoun(exclude = setOf(defined.word, noun.word))

        return "${defined.word.uppercase()}: ${noun.articulated} ${adj.forGender(noun.gender)} care ${verb.word} ${object_.articulated}."
    }
}
