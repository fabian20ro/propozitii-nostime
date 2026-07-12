package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.words.WordUtils

class VerseLineCapitalizer(private val provider: ISentenceProvider) : ISentenceProvider {

    override fun getSentence(): String {
        val raw = provider.getSentence()
        if (raw.isBlank()) return ""
        val parts = raw.split(Regex("\\s*/\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        return parts.joinToString(" / ") { WordUtils.capitalizeFirstLetter(it) ?: "" }
    }
}
