package scrabble.phrases.decorators

import org.slf4j.LoggerFactory
import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.words.WordUtils

class FirstSentenceLetterCapitalizer(private val provider: ISentenceProvider) : ISentenceProvider {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun getSentence(): String {
        return try {
            val raw = provider.getSentence() ?: ""
            if (raw.isBlank()) return ""
            val trimmed = raw.trim()
            // Strengthen error boundary: if capitalizeFirstLetter returns null,
            // it indicates an unexpected state in WordUtils — preserve original instead of masking with empty string.
            val result = WordUtils.capitalizeFirstLetter(trimmed) ?: trimmed
            result.ifBlank { "" }
        } catch (e: Exception) {
            logger.warn("FirstSentenceLetterCapitalizer provider failed, returning empty sentence", e)
            ""
        }
    }
}
