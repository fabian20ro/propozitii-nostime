package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class HtmlVerseBreakerTest {

    @Test
    fun `should replace verse delimiter with br tag`() {
        val mockProvider = ISentenceProvider { "Hello / World" }
        val breaker = HtmlVerseBreaker(mockProvider)
        assertEquals("Hello<br/>World", breaker.getSentence())
    }

    @Test
    fun `should replace verse delimiter with br tag even without spaces`() {
        val mockProvider = ISentenceProvider { "Hello/World" }
        val breaker = HtmlVerseBreaker(mockProvider)
        assertEquals("Hello<br/>World", breaker.getSentence())
    }

    @Test
    fun `should preserve html tags`() {
        val mockProvider = ISentenceProvider { "<p>Hello / World</p>" }
        val breaker = HtmlVerseBreaker(mockProvider)
        assertEquals("<p>Hello<br/>World</p>", breaker.getSentence())
    }

    @Test
    fun `should replace all verse delimiters in a sentence`() {
        val mockProvider = ISentenceProvider { "Line1 / Line2 / Line3" }
        val breaker = HtmlVerseBreaker(mockProvider)
        assertEquals("Line1<br/>Line2<br/>Line3", breaker.getSentence())
    }

    @Test
    fun `should return empty string for blank input`() {
        val mockProvider = ISentenceProvider { "   \t  " }
        val breaker = HtmlVerseBreaker(mockProvider)
        assertEquals("", breaker.getSentence())
    }
}
