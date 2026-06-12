package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class HtmlVerseBreakerTest {

    private class MockProvider(private val sentence: String) : ISentenceProvider {
        override fun getSentence(): String = sentence
    }

    @Test
    fun `should replace verse delimiter with br tag`() {
        val provider = MockProvider("Line 1 / Line 2")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("Line 1<br/>Line 2", breaker.getSentence())
    }

    @Test
    fun `should handle multiple delimiters`() {
        val provider = MockProvider("A / B / C")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("A<br/>B<br/>C", breaker.getSentence())
    }

    @Test
    fun `should do nothing if no delimiter present`() {
        val provider = MockProvider("No delimiter here")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("No delimiter here", breaker.getSentence())
    }

    @Test
    fun `should handle empty string`() {
        val provider = MockProvider("")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("", breaker.getSentence())
    }

    @Test
    fun `should handle leading and trailing delimiters`() {
        val provider = MockProvider(" / Start / End / ")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("<br/>Start<br/>End<br/>", breaker.getSentence())
    }

    @Test
    fun `should not replace slash inside HTML tags`() {
        val provider = MockProvider("<a href=\"https://dexonline.ro/definitie/word\">Link</a> / Next line")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("<a href=\"https://dexonline.ro/definitie/word\">Link</a><br/>Next line", breaker.getSentence())
    }

    @Test
    fun `should replace different spacing`() {
        // Previous implementation was very strict: only " / "
        val provider = MockProvider("Word/Word")
        val breaker = HtmlVerseBreaker(provider)
        assertEquals("Word<br/>Word", breaker.getSentence())

        val provider2 = MockProvider("Word /Word")
        val breaker2 = HtmlVerseBreaker(provider2)
        assertEquals("Word<br/>Word", breaker2.getSentence())

        val provider3 = MockProvider("Word/ Word")
        val breaker3 = HtmlVerseBreaker(provider3)
        assertEquals("Word<br/>Word", breaker3.getSentence())

        val provider4 = MockProvider("Word / Word")
        val breaker4 = HtmlVerseBreaker(provider4)
        assertEquals("Word<br/>Word", breaker4.getSentence())
    }
}
