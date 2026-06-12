package scrabble.phrases.decorators

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import scrabble.phrases.providers.ISentenceProvider

class DexlineLinkAdderTest {

    @Test
    fun shouldHandleSingleQuotes() {
        val baseProvider = object : ISentenceProvider {
            override fun getSentence(): String = "l'arbre"
        }
        val result = DexlineLinkAdder(baseProvider).getSentence()
        // Assuming the fix is implemented
        assertEquals("<a href=\"https://dexonline.ro/definitie/l%27arbre\" target=\"_blank\" rel=\"noopener\" data-word=\"l%27arbre\">l&#39;arbre</a>", result)
    }
}
