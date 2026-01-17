package scrabble.phrases.decorators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import scrabble.phrases.providers.ISentenceProvider;

/**
 * Tests for sentence decorators.
 */
class DecoratorTest {

    @Test
    void shouldDecorateWithLinksAndBreaks() {
        String expected = "<a href=\"https://dexonline.ro/definitie/ana\">Ana</a><div class=\"box\">"
            + "<iframe src=\"https://dexonline.ro/definitie/ana\" width = \"480px\" height = \"800px\"></iframe></div><br/>"
            + "<a href=\"https://dexonline.ro/definitie/are\">are</a><div class=\"box\">"
            + "<iframe src=\"https://dexonline.ro/definitie/are\" width = \"480px\" height = \"800px\"></iframe></div> "
            + "<a href=\"https://dexonline.ro/definitie/mere\">mere</a><div class=\"box\">"
            + "<iframe src=\"https://dexonline.ro/definitie/mere\" width = \"480px\" height = \"800px\"></iframe></div>.";

        ISentenceProvider baseProvider = () -> "ana / are mere.";
        ISentenceProvider decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(baseProvider)
            )
        );

        assertEquals(expected, decorated.getSentence());
    }

    @Test
    void shouldCapitalizeFirstLetter() {
        ISentenceProvider baseProvider = () -> "test sentence";
        ISentenceProvider capitalized = new FirstSentenceLetterCapitalizer(baseProvider);
        assertEquals("Test sentence", capitalized.getSentence());
    }

    @Test
    void shouldReplaceVerseBreaks() {
        ISentenceProvider baseProvider = () -> "line one / line two / line three";
        ISentenceProvider breaker = new HtmlVerseBreaker(baseProvider);
        assertEquals("line one<br/>line two<br/>line three", breaker.getSentence());
    }
}
