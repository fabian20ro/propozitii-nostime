package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.words.WordUtils

class VerseLineCapitalizer(private val provider: ISentenceProvider) : ISentenceProvider {

    override fun getSentence(): String {
        val raw = provider.getSentence()
        if (raw.isBlank()) return ""
        return raw.split(Regex("\\s*/\\s*"))
            .joinToString(" / ") { WordUtils.capitalizeFirstLetter(it.trim()) ?: "" }
    }
}
