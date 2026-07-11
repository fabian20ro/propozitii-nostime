package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class VerseLineCapitalizerTest {

    private class MockProvider(private val sentence: String) : ISentenceProvider {
        override fun getSentence(): String = sentence
    }

    @Test
    fun `should capitalize each part of a verse`() {
        val provider = MockProvider("line one / line two")
        val capitalizer = VerseLineCapitalizer(provider)
        assertEquals("Line one / Line two", capitalizer.getSentence())
    }

    @Test
    fun `should handle single line`() {
        val provider = MockProvider("hello world")
        val capitalizer = VerseLineCapitalizer(provider)
        assertEquals("Hello world", capitalizer.getSentence())
    }

    @Test
    fun `should handle multiple slashes`() {
        val provider = MockProvider("a / b / c")
        val capitalizer = VerseLineCapitalizer(provider)
        assertEquals("A / B / C", capitalizer.getSentence())
    }

    @Test
    fun `should handle different spacing around slash`() {
        val provider = MockProvider("line1/line2")
        val capitalizer = VerseLineCapitalizer(provider)
        assertEquals("Line1 / Line2", capitalizer.getSentence())
    }

    @Test
    fun `should return empty for blank input`() {
        val provider = MockProvider("")
        val capitalizer = VerseLineCapitalizer(provider)
        assertEquals("", capitalizer.getSentence())
    }

    @Test
    fun `should return empty for whitespace-only input`() {
        val provider = MockProvider("   ")
        val capitalizer = VerseLineCapitalizer(provider)
        assertEquals("", capitalizer.getSentence())
    }
}
