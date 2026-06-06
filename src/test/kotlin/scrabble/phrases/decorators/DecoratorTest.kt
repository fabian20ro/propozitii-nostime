package scrabble.phrases.decorators

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun shouldConvertSlashToBr() {
        val baseProvider = object : ISentenceProvider {
            override fun getSentence(): String = "ana / are"
        }
        val decorated = HtmlVerseBreaker(baseProvider)
        assertEquals("ana<br/>are", decorated.getSentence())
    }
}
