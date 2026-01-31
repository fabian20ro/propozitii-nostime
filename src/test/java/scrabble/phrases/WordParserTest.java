package scrabble.phrases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;

/**
 * Tests for WordParser.
 */
class WordParserTest {

    private final IWordFilter refuseAll = word -> false;
    private final IWordFilter wordsWithMoreThan10Chars = word -> word.word().length() > 10;

    @Test
    void shouldParseWordsFromReader() throws IOException {
        String input = "carte F\nfrumos A\nmerge VT\nbăiat M\ncasă F\nalerg V\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        WordDictionary dictionary = new WordParser().parse(reader);

        assertThat(dictionary.getRandomNoun()).isNotNull();
        assertThat(dictionary.getRandomAdjective()).isNotNull();
        assertThat(dictionary.getRandomVerb()).isNotNull();
        assertThat(dictionary.getTotalAcceptedWordCount()).isEqualTo(6);
    }

    @Test
    void shouldSkipLinesWithInsufficientParts() throws IOException {
        String input = "singleword\ncarte F\n";
        BufferedReader reader = new BufferedReader(new StringReader(input));

        WordDictionary dictionary = new WordParser().parse(reader);

        assertThat(dictionary.getTotalAcceptedWordCount()).isEqualTo(1);
    }

    @Test
    @Disabled("Integration test - takes too long for CI")
    void shouldParseAndFilterDictionary() throws IOException {
        long t1 = System.currentTimeMillis();

        WordDictionary dictionary = TestHelper.getPopulatedDictionaryFromIncludedFile();

        long t2 = System.currentTimeMillis();

        int accepted = dictionary.getTotalAcceptedWordCount();
        int refused = dictionary.getTotalRefusedWordCount();
        int unknown = dictionary.getTotalUnknownWordCount();
        int total = dictionary.getTotalWordCount();

        System.out.println(
            "accepted: " + accepted + "\nrefused: " + refused + "\nunknown: " + unknown + "\ntotal: " + total);

        assertNotNull(dictionary.getRandomNoun());
        assertNotNull(dictionary.getRandomAdjective());
        assertNotNull(dictionary.getRandomVerb());

        dictionary.addFilter(refuseAll);
        assertEquals(accepted, dictionary.getTotalRefusedWordCount());
        assertNull(dictionary.getRandomNoun());
        assertNull(dictionary.getRandomAdjective());
        assertNull(dictionary.getRandomVerb());
        assertEquals(total, dictionary.getTotalWordCount());

        long t3 = System.currentTimeMillis();

        dictionary.removeFilter(refuseAll);
        assertEquals(accepted, dictionary.getTotalAcceptedWordCount());
        assertNotNull(dictionary.getRandomNoun());
        assertNotNull(dictionary.getRandomAdjective());
        assertNotNull(dictionary.getRandomVerb());
        assertEquals(total, dictionary.getTotalWordCount());

        long t4 = System.currentTimeMillis();

        dictionary.addFilter(refuseAll);
        assertEquals(accepted, dictionary.getTotalRefusedWordCount());
        assertNull(dictionary.getRandomNoun());
        assertNull(dictionary.getRandomAdjective());
        assertNull(dictionary.getRandomVerb());
        assertEquals(total, dictionary.getTotalWordCount());

        long t5 = System.currentTimeMillis();

        dictionary.clearFilters();
        assertNotNull(dictionary.getRandomNoun());
        assertNotNull(dictionary.getRandomAdjective());
        assertNotNull(dictionary.getRandomVerb());
        assertEquals(total, dictionary.getTotalWordCount());

        long t6 = System.currentTimeMillis();

        dictionary.addFilter(wordsWithMoreThan10Chars);
        assertEquals(total, dictionary.getTotalWordCount());

        long t7 = System.currentTimeMillis();

        dictionary.removeFilter(wordsWithMoreThan10Chars);
        assertEquals(total, dictionary.getTotalWordCount());
        assertEquals(accepted, dictionary.getTotalAcceptedWordCount());

        long t8 = System.currentTimeMillis();

        System.out.println("Initial dictionary population took: " + (t2 - t1) + " millis.");
        System.out.println("Adding filter to remove them all took: " + (t3 - t2) + " millis.");
        System.out.println("Removing filter to add them all back took: " + (t4 - t3) + " millis.");
        System.out.println("Adding filter to remove them all again took: " + (t5 - t4) + " millis.");
        System.out.println("Clearing all filters took: " + (t6 - t5) + " millis.");
        System.out.println("Adding filter for words with more than 10 chars: " + (t7 - t6) + " millis.");
        System.out.println("Removing that filter: " + (t8 - t7) + " millis.");
        System.out.println("Overall, it took: " + (t8 - t1) + " millis.");
    }
}
