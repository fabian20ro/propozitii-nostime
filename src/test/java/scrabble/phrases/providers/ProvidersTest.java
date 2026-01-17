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
    void shouldGenerateCouplet() throws IOException {
        CoupletProvider provider = new CoupletProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated couplet: " + sentence);
    }

    @Test
    void shouldGenerateComparison() throws IOException {
        ComparisonProvider provider = new ComparisonProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated comparison: " + sentence);
    }

    @Test
    void shouldGenerateDefinition() throws IOException {
        DefinitionProvider provider = new DefinitionProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated definition: " + sentence);
    }

    @Test
    void shouldGenerateTautogram() throws IOException {
        TautogramProvider provider = new TautogramProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated tautogram: " + sentence);
    }

    @Test
    void shouldGenerateMirror() throws IOException {
        MirrorProvider provider = new MirrorProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertNotNull(sentence);
        System.out.println("Generated mirror: " + sentence);
    }
}
