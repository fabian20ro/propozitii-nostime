package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider

class HtmlVerseBreaker(private val provider: ISentenceProvider) : ISentenceProvider {
    override fun getSentence(): String =
        provider.getSentence().replace(" / ", "<br/>")
}
