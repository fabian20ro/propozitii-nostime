package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider

class HtmlVerseBreaker(private val provider: ISentenceProvider) : ISentenceProvider {
    override fun getSentence(): String {
        val text = provider.getSentence()
            ?: throw IllegalArgumentException("sentence is null")
        // Guard empty/whitespace-only input — avoids regex compilation and protects the
        // " / " → <br/> contract boundary (AGENTS.md constraint #1).
        if (text.isBlank()) return ""

        return text.replace(Regex("(<!--.*?-->|<[^>]*>)|(\\s*/\\s*)")) { match ->
            // Preserve HTML comments and tags verbatim — never re-process already-decorated content.
            if (match.groupValues[1].isNotEmpty()) match.value else "<br/>"
        }
    }
}
