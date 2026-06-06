package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class DecoratorTest {

    @Test
    fun shouldDecorateWithLinksAndBreaks() {
        val expected = "<a href=\"https://dexonline.ro/definitie/ana\" target=\"_blank\" rel=\"noopener\" data-word=\"ana\">Ana</a><br/><a href=\"https://dexonline.ro/definitie/are\" target=\"_blank\" rel=\"noopener\" data-word=\"are\">Are</a><a href=\"https://dexonline.ro/definitie/mere\" target=\"_blank\" rel="noopener" data-word="mere">mere</a>."
        
        val baseProvider = ISentenceProvider { "ana / are mere." }
        val decorated = DexlineLinkAdder(
            HtmlVerseBreaker(
                VerseLineCapitalizer(baseProvider)
            )
        )

        assertEquals(expected, decorated.getSentence())
    }

    @Test
    fun shouldUrlEncodeWordsInHref() {
        val baseProvider = ISentenceProvider { "p\u0103\u021Bit" }
        val adder = DexlineLinkAdder(baseProvider)
        val result = adder.getSentence()

        assert(result.contains("href=\"https://dexonline.ro/definitie/p%C4%83%C8%9Bit\"")) {
            "Expected URL-encoded href, got: $result"
        }
        assert(result.contains("data-word=\"p%C4%83%C8%9Bit\"")) {
            "Expected data-word attribute, got: $result"
        }
        assert(result.contains(">p\u0103\u021Bit</a>")) {
            "Expected readable text content, got: $result"
        }
    }

    @Test
    fun shouldCapitalizeFirstLetter() {
        val baseProvider = ISentenceProvider { "test sentence" }
        val capitalized = FirstSentenceLetterCapitalizer(baseProvider)
        assertEquals("Test sentence", capitalized.getSentence())
    }

    @Test
    fun shouldHandleEmptyString() {
        val baseProvider = ISentenceProvider { "" }
        val capitalized = FirstSentenceLetterCapitalizer(baseProvider)
        assertEquals("", capitalized.getSentence())
    }


    @Test
    fun `should capitalize each verse line`() {
        val baseProvider = ISentenceProvider { "first line / second line / third line" }
        val capitalized = VerseLineCapitalizer(baseProvider)
        assertEquals("First line / Second line / Third line", capitalized.getSentence())

        val provider2 = ISentenceProvider { "first line/second line" }
        val capitalized2 = VerseLineCapitalizer(provider2)
        assertEquals("First line / Second line", capitalized2.getSentence())
    }

    @Test
    fun `should replace verse breaks`() {
        val baseProvider = ISentenceProvider { "line one / line two / line three" }
        val breaker = HtmlVerseBreaker(baseProvider)
        assertEquals("line one<br/>line two<br/>line three", breaker.getSentence())
        
        val provider2 = ISentenceProvider { "line one/line two" }
        val breaker2 = HtmlVerseBreaker(provider2)
        assertEquals("line one<br/>line two", breaker2.getSentence())
    }

    @Test
    fun shouldKeepAnchorContract() {
        val baseProvider = ISentenceProvider { "Masă" }
        val result = DexlineLinkAdder(baseProvider).getSentence()

        assertTrue(result.contains("""href="${DexlineLinkAdder.DEXONLINE_URL}mas%C4%83""""))
        assertTrue(result.contains("""target="${DexlineLinkAdder.LINK_TARGET}""""))
        assertTrue(result.contains("""rel="${DexlineLinkAdder.LINK_REL}""""))
        assertTrue(result.contains("""${DexlineLinkAdder.ATTR_DATA_WORD}="mas%C4%83""""))
        assertFalse(result.contains("onclick="))
    }
}
