package scrabble.phrases.decorators

import scrabble.phrases.providers.ISentenceProvider
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class DexlineLinkAdder(private val provider: ISentenceProvider) : ISentenceProvider {

    companion object {
        const val DEXONLINE_URL = "https://dexonline.ro/definitie/"
        const val LINK_TARGET = "_blank"
        const val LINK_REL = "noopener"
        const val ATTR_DATA_WORD = "data-word"

        /** Characters that must not appear in encoded URLs or displayed text. */
        private val DANGEROUS_CHARS = Regex("[<>\"']")
    }

    override fun getSentence(): String {
        val raw = provider.getSentence()
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("[\\p{L}']+")) { addHref(it.value) }
    }

    private fun addHref(word: String): String {
        val encodedWord = URLEncoder.encode(word.lowercase(), StandardCharsets.UTF_8)
        require(!DANGEROUS_CHARS.containsMatchIn(encodedWord)) { "encoded word contains HTML-metacharacters; href would be unsafe" }
        val url = "$DEXONLINE_URL$encodedWord"
        val escapedWord = escapeHtml(word)
        return """<a href="$url" target="$LINK_TARGET" rel="$LINK_REL" $ATTR_DATA_WORD="$encodedWord">$escapedWord</a>"""
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
