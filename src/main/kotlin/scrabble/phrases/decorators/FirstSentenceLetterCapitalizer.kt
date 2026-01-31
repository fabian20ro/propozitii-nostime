package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.words.WordUtils

class FirstSentenceLetterCapitalizer(private val provider: ISentenceProvider) : ISentenceProvider {
    override fun getSentence(): String =
        WordUtils.capitalizeFirstLetter(provider.getSentence().trim()) ?: ""
}
