package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class DecoratorTest {

    @Test
    fun shouldDecorateWithLinksAndBreaks() {
        val expected = "<a href=\"https://dexonline.ro/definitie/ana\" target=\"_blank\" rel=\"noopener\" data-word=\"ana\">Ana</a><br/>" +
            "<a href=\"https://dexonline.ro/definitie/are\" target=\"_blank\" rel=\"noopener\" data-word=\"are\">are</a> " +
            "<a href=\"https://dexonline.ro/definitie/mere\" target=\"_blank\" rel=\"noopener\" data-word=\"mere\">mere</a>."

        val baseProvider = ISentenceProvider { "ana / are mere." }
        val decorated = HtmlVerseBreaker(
            DexonlineLinkAdder(
                FirstSentenceLetterCapitalizer(baseProvider)
            )
        )

        assertEquals(expected, decorated.getSentence())
    }

    @Test
    fun shouldUrlEncodeWordsInHref() {
        val baseProvider = ISentenceProvider { "p\u0103\u021Bit" }
        val adder = DexonlineLinkAdder(baseProvider)
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
    fun shouldReplaceVerseBreaks() {
        val baseProvider = ISentenceProvider { "line one / line two / line three" }
        val breaker = HtmlVerseBreaker(baseProvider)
        assertEquals("line one<br/>line two<br/>line three", breaker.getSentence())
    }
}
