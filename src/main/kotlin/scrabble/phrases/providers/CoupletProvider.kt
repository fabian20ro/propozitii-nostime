package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.NounGender

class CoupletProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        // Find a rhyme group with at least 4 nouns
        val rhyme = repo.findRhymeGroup("N", 4)
            ?: throw IllegalStateException("No rhyme group with 4+ nouns found")

        val noun1 = repo.getRandomNounByRhyme(rhyme)
            ?: throw IllegalStateException("No noun found for rhyme '$rhyme'")
        val adj1 = repo.getRandomAdjective()
        val verb1 = repo.getRandomVerb()
        val nounEnd1 = repo.getRandomNounByRhyme(rhyme)
            ?: throw IllegalStateException("No second noun found for rhyme '$rhyme'")

        val adjForm1 = if (noun1.gender == NounGender.F) adj1.feminine else adj1.word
        val line1 = "${noun1.articulated} $adjForm1 ${verb1.word} ${nounEnd1.articulated}."

        val noun2 = repo.getRandomNounByRhyme(rhyme)
            ?: throw IllegalStateException("No third noun found for rhyme '$rhyme'")
        val adj2 = repo.getRandomAdjective()
        val verb2 = repo.getRandomVerb()
        val nounEnd2 = repo.getRandomNounByRhyme(rhyme)
            ?: throw IllegalStateException("No fourth noun found for rhyme '$rhyme'")

        val adjForm2 = if (noun2.gender == NounGender.F) adj2.feminine else adj2.word
        val line2 = "${noun2.articulated} $adjForm2 ${verb2.word} ${nounEnd2.articulated}."

        val line2Capitalized = line2.replaceFirstChar { it.uppercase() }

        return "$line1 / $line2Capitalized"
    }
}
