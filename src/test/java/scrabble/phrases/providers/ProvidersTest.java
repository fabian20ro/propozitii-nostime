package scrabble.phrases.providers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import scrabble.phrases.TestHelper;

/**
 * Tests for sentence providers.
 */
class ProvidersTest {

    @Test
    void shouldGenerateHaiku() throws IOException {
        HaikuProvider provider = new HaikuProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated haiku: " + sentence);
    }

    @Test
    void shouldGenerateFiveWordSentence() throws IOException {
        FiveWordSentenceProvider provider = new FiveWordSentenceProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated five-word sentence: " + sentence);
    }
}
