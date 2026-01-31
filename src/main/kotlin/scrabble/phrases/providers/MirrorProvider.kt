package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository
import scrabble.phrases.words.Noun
import scrabble.phrases.words.NounGender

class MirrorProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        // Find two rhyme groups with at least 2 nouns each
        val rhymes = repo.findTwoRhymeGroups("N", 2)

        val nounsA: List<Noun>
        val nounsB: List<Noun>

        if (rhymes != null) {
            nounsA = repo.getNounsByRhyme(rhymes.first, 4)
            nounsB = repo.getNounsByRhyme(rhymes.second, 4)
        } else {
            // Fallback: use random nouns
            nounsA = listOf(repo.getRandomNoun(), repo.getRandomNoun())
            nounsB = listOf(repo.getRandomNoun(), repo.getRandomNoun())
        }

        val line1 = buildLine(nounsA) + ","
        val line2 = buildLine(nounsB) + ","
        val line3 = buildLine(nounsB) + ","
        val line4 = buildLine(nounsA) + "."

        return "$line1 / $line2 / $line3 / $line4"
    }

    private fun buildLine(nouns: List<Noun>): String {
        if (nouns.isEmpty()) throw IllegalStateException("No nouns available for mirror line")
        val noun = nouns.random()
        val adj = repo.getRandomAdjective()
        val verb = repo.getRandomVerb()

        val adjForm = if (noun.gender == NounGender.F) adj.feminine else adj.word
        return "${noun.articulated} $adjForm ${verb.word}"
    }
}
