package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class MirrorProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val rhymes = repo.findTwoRhymeGroups("V", 2, minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("Cannot find 2 verb rhyme groups with 2+ verbs each")

        val (rhymeA, rhymeB) = rhymes
        val usedNouns = mutableSetOf<String>()
        val usedAdjs = mutableSetOf<String>()
        val usedVerbs = mutableSetOf<String>()

        fun buildLine(rhyme: String, punct: String): String {
            val noun = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = usedNouns)
            usedNouns.add(noun.word)
            val adj = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity, exclude = usedAdjs)
            usedAdjs.add(adj.word)
            val verb = repo.getRandomVerbByRhyme(rhyme, minRarity = minRarity, maxRarity = maxRarity, exclude = usedVerbs)
                ?: throw IllegalStateException("No verb found for rhyme '$rhyme'")
            usedVerbs.add(verb.word)
            return "${noun.articulated} ${adj.forGender(noun.gender)} ${verb.word}$punct"
        }

        // ABBA rhyme pattern
        return listOf(
            buildLine(rhymeA, ","),
            buildLine(rhymeB, ","),
            buildLine(rhymeB, ","),
            buildLine(rhymeA, ".")
        ).joinToString(" / ")
    }
}
