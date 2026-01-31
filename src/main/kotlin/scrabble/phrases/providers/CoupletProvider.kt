package scrabble.phrases.providers

import scrabble.phrases.repository.WordRepository

class CoupletProvider(private val repo: WordRepository) : ISentenceProvider {

    override fun getSentence(): String {
        val rhymes = repo.findTwoRhymeGroups("N", 2)
            ?: throw IllegalStateException("Cannot find 2 rhyme groups with 2+ nouns each")

        val rhymeA = rhymes.first
        val rhymeB = rhymes.second

        // Pick all constrained (rhymed) line-ending nouns FIRST,
        // before unconstrained line-start nouns can deplete rhyme groups.
        val usedNouns = mutableSetOf<String>()

        val nounEnd1 = repo.getRandomNounByRhyme(rhymeA, exclude = usedNouns)
            ?: throw IllegalStateException("No noun found for rhyme '$rhymeA'")
        usedNouns.add(nounEnd1.word)

        val nounEnd2 = repo.getRandomNounByRhyme(rhymeB, exclude = usedNouns)
            ?: throw IllegalStateException("No noun found for rhyme '$rhymeB'")
        usedNouns.add(nounEnd2.word)

        val nounEnd3 = repo.getRandomNounByRhyme(rhymeB, exclude = usedNouns)
            ?: throw IllegalStateException("No second noun found for rhyme '$rhymeB'")
        usedNouns.add(nounEnd3.word)

        val nounEnd4 = repo.getRandomNounByRhyme(rhymeA, exclude = usedNouns)
            ?: throw IllegalStateException("No second noun found for rhyme '$rhymeA'")
        usedNouns.add(nounEnd4.word)

        // Now pick unconstrained line-start nouns
        val noun1 = repo.getRandomNoun(exclude = usedNouns)
        usedNouns.add(noun1.word)
        val noun2 = repo.getRandomNoun(exclude = usedNouns)
        usedNouns.add(noun2.word)
        val noun3 = repo.getRandomNoun(exclude = usedNouns)
        usedNouns.add(noun3.word)
        val noun4 = repo.getRandomNoun(exclude = usedNouns)

        // Pick adjectives and verbs (all distinct)
        val usedAdjs = mutableSetOf<String>()
        val usedVerbs = mutableSetOf<String>()

        val adj1 = repo.getRandomAdjective()
        usedAdjs.add(adj1.word)
        val adj2 = repo.getRandomAdjective(exclude = usedAdjs)
        usedAdjs.add(adj2.word)
        val adj3 = repo.getRandomAdjective(exclude = usedAdjs)
        usedAdjs.add(adj3.word)
        val adj4 = repo.getRandomAdjective(exclude = usedAdjs)

        val verb1 = repo.getRandomVerb()
        usedVerbs.add(verb1.word)
        val verb2 = repo.getRandomVerb(exclude = usedVerbs)
        usedVerbs.add(verb2.word)
        val verb3 = repo.getRandomVerb(exclude = usedVerbs)
        usedVerbs.add(verb3.word)
        val verb4 = repo.getRandomVerb(exclude = usedVerbs)

        // Assemble ABBA: lines 1&4 end with rhyme A, lines 2&3 end with rhyme B
        val line1 = "${noun1.articulated} ${adj1.forGender(noun1.gender)} ${verb1.word} ${nounEnd1.articulated}."
        val line2 = "${noun2.articulated} ${adj2.forGender(noun2.gender)} ${verb2.word} ${nounEnd2.articulated}."
        val line3 = "${noun3.articulated} ${adj3.forGender(noun3.gender)} ${verb3.word} ${nounEnd3.articulated}."
        val line4 = "${noun4.articulated} ${adj4.forGender(noun4.gender)} ${verb4.word} ${nounEnd4.articulated}."

        return "$line1 / $line2 / $line3 / $line4"
    }
}
