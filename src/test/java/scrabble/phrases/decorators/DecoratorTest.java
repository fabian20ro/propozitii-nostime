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
        // Words are URL-encoded in href/src and HTML-escaped in text content
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
    void shouldUrlEncodeWordsInHref() {
        ISentenceProvider baseProvider = () -> "pățit";
        DexonlineLinkAdder adder = new DexonlineLinkAdder(baseProvider);
        String result = adder.getSentence();

        // Romanian diacritics should be URL-encoded in href/src
        assert result.contains("href=\"https://dexonline.ro/definitie/p%C4%83%C8%9Bit\"")
            : "Expected URL-encoded href, got: " + result;
        // But displayed text should be HTML-escaped (unchanged for normal chars)
        assert result.contains(">pățit</a>") : "Expected readable text content, got: " + result;
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
