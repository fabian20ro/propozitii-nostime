package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class DecoratorTest {

    @Test
    fun shouldDecorateWithLinksAndBreaks() {
        val expected = "<a href=\"https://dexonline.ro/definitie/ana\">Ana</a><div class=\"box\">" +
            "<iframe src=\"https://dexonline.ro/definitie/ana\" width = \"480px\" height = \"800px\"></iframe></div><br/>" +
            "<a href=\"https://dexonline.ro/definitie/are\">are</a><div class=\"box\">" +
            "<iframe src=\"https://dexonline.ro/definitie/are\" width = \"480px\" height = \"800px\"></iframe></div> " +
            "<a href=\"https://dexonline.ro/definitie/mere\">mere</a><div class=\"box\">" +
            "<iframe src=\"https://dexonline.ro/definitie/mere\" width = \"480px\" height = \"800px\"></iframe></div>."

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
