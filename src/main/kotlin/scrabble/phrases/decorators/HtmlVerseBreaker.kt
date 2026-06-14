package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider

class HtmlVerseBreaker(private val provider: ISentenceProvider) : ISentenceProvider {
    override fun getSentence(): String {
        val text = provider.getSentence()
        return text.replace(Regex("(<[^>]*>)|(\\s*/\\s*)")) {
            if (it.value.startsWith("<")) {
                it.value
            } else if (it.range.last + 1 < text.length && text[it.range.last + 1] == '<') {
                it.value
            } else {
                "<br/>"
            }
        }
    }
}
