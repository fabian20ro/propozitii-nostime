package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class DecoratorTest {

    @Test
    fun shouldKeepAnchorContract() {
        val baseProvider = object : ISentenceProvider {
            override fun getSentence(): String = "Masă"
        }
        val result = DexlineLinkAdder(baseProvider).getSentence()

        assertEquals("<a href=\"https://dexonline.ro/definitie/mas%C4%83\" target=\"_blank\" rel=\"noopener\" data-word=\"mas%C4%83\">Masă</a>", result)
    }

    @Test
    fun shouldComposeDecoratorsCorrectly() {
        val baseProvider = object : ISentenceProvider {
            override fun getSentence(): String = "ana / are"
        }
        val decorated = HtmlVerseBreaker(DexlineLinkAdder(baseProvider))
        val result = decorated.getSentence()

        assertTrue(result.contains("<a href=\"https://dexonline.ro/definitie/ana\""))
        assertTrue(result.contains("<br/>"))
        assertTrue(result.contains("<a href=\"https://dexonline.ro/definitie/are\""))

        val expected = "<a href=\"https://dexonline.ro/definitie/ana\" target=\"_blank\" rel=\"noopener\" data-word=\"ana\">ana</a><br/><a href=\"https://dexonline.ro/definitie/are\" target=\"_blank\" rel=\"noopener\" data-word=\"are\">are</a>"
        assertEquals(expected, result)
    }
}
