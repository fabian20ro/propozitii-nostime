package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class CoupletProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val rhymes = repo.findTwoRhymeGroups("N", 3)
            ?: throw IllegalStateException("Cannot find 2 rhyme groups with 3+ nouns each")

        val rhymeA = rhymes.first
        val rhymeB = rhymes.second

        val usedNouns = mutableSetOf<String>()
        val usedAdjs = mutableSetOf<String>()
        val usedVerbs = mutableSetOf<String>()

        // Line 1: rhyme A
        val noun1 = repo.getRandomNounByRhyme(rhymeA)
            ?: throw IllegalStateException("No noun found for rhyme '$rhymeA'")
        usedNouns.add(noun1.word)
        val adj1 = repo.getRandomAdjective()
        usedAdjs.add(adj1.word)
        val verb1 = repo.getRandomVerb()
        usedVerbs.add(verb1.word)
        val nounEnd1 = repo.getRandomNounByRhyme(rhymeA, exclude = usedNouns)
            ?: throw IllegalStateException("No second noun found for rhyme '$rhymeA'")
        usedNouns.add(nounEnd1.word)
        val line1 = "${noun1.articulated} ${adj1.forGender(noun1.gender)} ${verb1.word} ${nounEnd1.articulated}."

        // Line 2: rhyme B
        val noun2 = repo.getRandomNounByRhyme(rhymeB)
            ?: throw IllegalStateException("No noun found for rhyme '$rhymeB'")
        usedNouns.add(noun2.word)
        val adj2 = repo.getRandomAdjective(exclude = usedAdjs)
        usedAdjs.add(adj2.word)
        val verb2 = repo.getRandomVerb(exclude = usedVerbs)
        usedVerbs.add(verb2.word)
        val nounEnd2 = repo.getRandomNounByRhyme(rhymeB, exclude = usedNouns)
            ?: throw IllegalStateException("No second noun found for rhyme '$rhymeB'")
        usedNouns.add(nounEnd2.word)
        val line2 = "${noun2.articulated} ${adj2.forGender(noun2.gender)} ${verb2.word} ${nounEnd2.articulated}."

        // Line 3: rhyme B
        val noun3 = repo.getRandomNoun(exclude = usedNouns)
        usedNouns.add(noun3.word)
        val adj3 = repo.getRandomAdjective(exclude = usedAdjs)
        usedAdjs.add(adj3.word)
        val verb3 = repo.getRandomVerb(exclude = usedVerbs)
        usedVerbs.add(verb3.word)
        val nounEnd3 = repo.getRandomNounByRhyme(rhymeB, exclude = usedNouns)
            ?: throw IllegalStateException("No third noun found for rhyme '$rhymeB'")
        usedNouns.add(nounEnd3.word)
        val line3 = "${noun3.articulated} ${adj3.forGender(noun3.gender)} ${verb3.word} ${nounEnd3.articulated}."

        // Line 4: rhyme A
        val noun4 = repo.getRandomNoun(exclude = usedNouns)
        usedNouns.add(noun4.word)
        val adj4 = repo.getRandomAdjective(exclude = usedAdjs)
        usedAdjs.add(adj4.word)
        val verb4 = repo.getRandomVerb(exclude = usedVerbs)
        usedVerbs.add(verb4.word)
        val nounEnd4 = repo.getRandomNounByRhyme(rhymeA, exclude = usedNouns)
            ?: throw IllegalStateException("No third noun found for rhyme '$rhymeA'")
        val line4 = "${noun4.articulated} ${adj4.forGender(noun4.gender)} ${verb4.word} ${nounEnd4.articulated}."

        return "$line1 / $line2 / $line3 / $line4"
    }
}
