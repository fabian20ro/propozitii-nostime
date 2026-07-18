package scrabble.phrases.providers

import org.jboss.logging.Logger
import scrabble.phrases.repository.WordRepository

class TautogramProvider(
    private val repo: WordRepository,
    private val minRarity: Int = 1,
    private val maxRarity: Int
) : ISentenceProvider {

    companion object {
        @JvmField val log: Logger = Logger.getLogger(TautogramProvider::class.java)
    }

    override fun getSentence(): String {
        // Pick a random two-letter prefix that has nouns, adjectives, and verbs
        val prefix = repo.getRandomPrefixWithAllTypes(minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("[Tautogram] No two-letter prefix with all word types (rarity=$minRarity..$maxRarity)")

        log.debugf("Tautogram selected prefix='%s' for rarity %d..%d", prefix, minRarity, maxRarity)

        val noun1 = repo.getRandomNounByPrefix(prefix, minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("[Tautogram] No noun for prefix '$prefix' (rarity=$minRarity..$maxRarity)")
        val adj = repo.getRandomAdjectiveByPrefix(prefix, minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("[Tautogram] No adjective for prefix '$prefix' (rarity=$minRarity..$maxRarity)")
        val verb = repo.getRandomVerbByPrefix(prefix, minRarity = minRarity, maxRarity = maxRarity)
            ?: throw IllegalStateException("[Tautogram] No verb for prefix '$prefix' (rarity=$minRarity..$maxRarity)")
        val noun2 = repo.getRandomNounByPrefix(prefix, minRarity = minRarity, maxRarity = maxRarity, exclude = setOf(noun1.word))
            ?: throw IllegalStateException("[Tautogram] No second noun for prefix '$prefix' excluding '${noun1.word}' (rarity=$minRarity..$maxRarity)")

        val words = listOf(noun1.word, adj.word, verb.word, noun2.word)
        for (word in words) {
            if (!word.lowercase().startsWith(prefix.lowercase())) {
                throw IllegalStateException("[Tautogram] Word '$word' does not start with prefix '$prefix'")
            }
        }

        return "${noun1.articulated} ${adj.forGender(noun1.gender)} ${verb.word} ${noun2.articulated}."
    }
}
