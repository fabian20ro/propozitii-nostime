package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class DexonlineLinkAdder(private val provider: ISentenceProvider) : ISentenceProvider {

    companion object {
        private const val DEXONLINE_URL = "https://dexonline.ro/definitie/"
    }

    override fun getSentence(): String {
        val sentence = provider.getSentence()
        val words = sentence.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val spaces = sentence.split(Regex("\\p{L}+"))
        val buffer = StringBuilder()
        var wordIndex = 0
        var spaceIndex = 0

        if (spaces.size > words.size) {
            buffer.append(spaces[spaceIndex++])
        }
        while (wordIndex < words.size && spaceIndex < spaces.size) {
            buffer.append(addHref(words[wordIndex++]))
            buffer.append(spaces[spaceIndex++])
        }
        if (wordIndex < words.size) {
            buffer.append(addHref(words[wordIndex]))
        }
        return buffer.toString()
    }

    private fun addHref(word: String): String {
        val encodedWord = URLEncoder.encode(word.lowercase(), StandardCharsets.UTF_8)
        val url = "$DEXONLINE_URL$encodedWord"
        val escapedWord = escapeHtml(word)
        return """<a href="$url" target="_blank" rel="noopener" data-word="$encodedWord">$escapedWord</a>"""
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
