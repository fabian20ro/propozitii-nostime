package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class DistihProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    override fun getSentence(): String {
        val usedNouns = mutableSetOf<String>()
        val usedAdjs = mutableSetOf<String>()
        val usedVerbs = mutableSetOf<String>()

        fun nextNoun() = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = usedNouns)
            .also { usedNouns.add(it.word) }
        fun nextAdj() = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity, exclude = usedAdjs)
            .also { usedAdjs.add(it.word) }
        fun nextVerb() = repo.getRandomVerb(minRarity = minRarity, maxRarity = maxRarity, exclude = usedVerbs)
            .also { usedVerbs.add(it.word) }

        fun buildLine(): String {
            val n1 = nextNoun()
            val a1 = nextAdj()
            val v = nextVerb()
            val n2 = nextNoun()
            val a2 = nextAdj()
            return "${n1.articulated} ${a1.forGender(n1.gender)} ${v.word} ${n2.articulated} ${a2.forGender(n2.gender)}."
        }

        return "${buildLine()} / ${buildLine()}"
    }
}
