package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.words.WordUtils

class VerseLineCapitalizer(private val provider: ISentenceProvider) : ISentenceProvider {
    override fun getSentence(): String =
        provider.getSentence()
            .split(" / ")
            .joinToString(" / ") { WordUtils.capitalizeFirstLetter(it.trim()) ?: "" }
}
