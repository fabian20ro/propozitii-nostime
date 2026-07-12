package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.words.WordUtils

class FirstSentenceLetterCapitalizer(private val provider: ISentenceProvider) : ISentenceProvider {
    override fun getSentence(): String {
        val raw = provider.getSentence()
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        // Strengthen error boundary: if capitalizeFirstLetter returns null,
        // it indicates an unexpected state in WordUtils — preserve original instead of masking with empty string.
        val result = WordUtils.capitalizeFirstLetter(trimmed) ?: trimmed
        return if (result.isEmpty()) "" else result
    }
}
