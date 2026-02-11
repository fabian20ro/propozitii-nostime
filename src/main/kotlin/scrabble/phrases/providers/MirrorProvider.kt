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

        val rhymeA = rhymes.first
        val rhymeB = rhymes.second

        val usedNouns = mutableSetOf<String>()
        val usedAdjs = mutableSetOf<String>()
        val usedVerbs = mutableSetOf<String>()

        // Line 1: ends with verb from rhyme A
        val noun1 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity)
        usedNouns.add(noun1.word)
        val adj1 = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity)
        usedAdjs.add(adj1.word)
        val verb1 = repo.getRandomVerbByRhyme(rhymeA, minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("No verb found for rhyme '$rhymeA'")
        usedVerbs.add(verb1.word)
        val line1 = "${noun1.articulated} ${adj1.forGender(noun1.gender)} ${verb1.word},"

        // Line 2: ends with verb from rhyme B
        val noun2 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = usedNouns)
        usedNouns.add(noun2.word)
        val adj2 = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity, exclude = usedAdjs)
        usedAdjs.add(adj2.word)
        val verb2 = repo.getRandomVerbByRhyme(rhymeB, minRarity = minRarity, maxRarity = maxRarity, exclude = usedVerbs)
            ?: throw IllegalStateException("No verb found for rhyme '$rhymeB'")
        usedVerbs.add(verb2.word)
        val line2 = "${noun2.articulated} ${adj2.forGender(noun2.gender)} ${verb2.word},"

        // Line 3: ends with verb from rhyme B
        val noun3 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = usedNouns)
        usedNouns.add(noun3.word)
        val adj3 = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity, exclude = usedAdjs)
        usedAdjs.add(adj3.word)
        val verb3 = repo.getRandomVerbByRhyme(rhymeB, minRarity = minRarity, maxRarity = maxRarity, exclude = usedVerbs)
            ?: throw IllegalStateException("No second verb found for rhyme '$rhymeB'")
        usedVerbs.add(verb3.word)
        val line3 = "${noun3.articulated} ${adj3.forGender(noun3.gender)} ${verb3.word},"

        // Line 4: ends with verb from rhyme A
        val noun4 = repo.getRandomNoun(minRarity = minRarity, maxRarity = maxRarity, exclude = usedNouns)
        usedNouns.add(noun4.word)
        val adj4 = repo.getRandomAdjective(minRarity = minRarity, maxRarity = maxRarity, exclude = usedAdjs)
        usedAdjs.add(adj4.word)
        val verb4 = repo.getRandomVerbByRhyme(rhymeA, minRarity = minRarity, maxRarity = maxRarity, exclude = usedVerbs)
            ?: throw IllegalStateException("No second verb found for rhyme '$rhymeA'")
        val line4 = "${noun4.articulated} ${adj4.forGender(noun4.gender)} ${verb4.word}."

        return "$line1 / $line2 / $line3 / $line4"
    }
}
