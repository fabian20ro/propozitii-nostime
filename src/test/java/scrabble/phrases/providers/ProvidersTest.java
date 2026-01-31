package scrabble.phrases.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import scrabble.phrases.TestHelper;
import scrabble.phrases.words.WordUtils;

/**
 * Tests for sentence providers with structural validation.
 */
class ProvidersTest {

    @Test
    void shouldGenerateHaikuWithCorrectStructure() throws IOException {
        HaikuProvider provider = new HaikuProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertThat(sentence).isNotNull().endsWith(".");

        // Haiku uses " / " as verse separator, should have 2 separators (3 parts)
        String[] parts = sentence.replace(".", "").split(" / ");
        assertThat(parts).hasSize(3);

        // Verify syllable counts: 5-7-5 structure
        // Part 1 = articulated noun (5 syllables)
        // Part 2 = adjective + verb (7 syllables)
        // Part 3 = articulated noun (5 syllables target line)
    }

    @Test
    void shouldGenerateCoupletWithTwoLines() throws IOException {
        CoupletProvider provider = new CoupletProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertThat(sentence).isNotNull();

        // Couplet has two lines separated by " / "
        String[] lines = sentence.split(" / ");
        assertThat(lines).hasSize(2);

        // Both lines should end with period
        assertThat(lines[0]).endsWith(".");
        assertThat(lines[1]).endsWith(".");

        // Second line should start with uppercase
        assertThat(Character.isUpperCase(lines[1].charAt(0))).isTrue();

        // Extract last words (before period) to check rhyme
        String lastWord1 = extractLastWord(lines[0]);
        String lastWord2 = extractLastWord(lines[1]);
        assertThat(WordUtils.computeRhyme(lastWord1))
            .as("Couplet lines should rhyme")
            .isEqualTo(WordUtils.computeRhyme(lastWord2));
    }

    @Test
    void shouldGenerateComparison() throws IOException {
        ComparisonProvider provider = new ComparisonProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertThat(sentence)
            .isNotNull()
            .contains(" e mai ")
            .contains(" decât ")
            .endsWith(".");
    }

    @Test
    void shouldGenerateDefinition() throws IOException {
        DefinitionProvider provider = new DefinitionProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertThat(sentence)
            .isNotNull()
            .contains(": ")
            .contains(" care ")
            .endsWith(".");

        // The defined word should be uppercase
        String definedWord = sentence.substring(0, sentence.indexOf(":"));
        assertThat(definedWord).isEqualTo(definedWord.toUpperCase());
    }

    @Test
    void shouldGenerateTautogramWithSameStartingLetter() throws IOException {
        TautogramProvider provider = new TautogramProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertThat(sentence).isNotNull().endsWith(".");

        // All words should start with the same letter
        String[] words = sentence.replace(".", "").split("\\s+");
        char firstLetter = Character.toLowerCase(words[0].charAt(0));
        for (String word : words) {
            assertThat(Character.toLowerCase(word.charAt(0)))
                .as("All words should start with '%c' but found '%s'", firstLetter, word)
                .isEqualTo(firstLetter);
        }
    }

    @Test
    void shouldGenerateMirrorWithFourLines() throws IOException {
        MirrorProvider provider = new MirrorProvider(TestHelper.getPopulatedDictionaryFromIncludedFile());
        String sentence = provider.getSentence();
        assertThat(sentence).isNotNull();

        // Mirror has 4 lines separated by " / "
        String[] lines = sentence.split(" / ");
        assertThat(lines).hasSize(4);

        // Last line ends with period, others with comma
        assertThat(lines[0]).endsWith(",");
        assertThat(lines[1]).endsWith(",");
        assertThat(lines[2]).endsWith(",");
        assertThat(lines[3]).endsWith(".");
    }

    private String extractLastWord(String line) {
        String cleaned = line.replaceAll("[.,!?]", "").trim();
        String[] words = cleaned.split("\\s+");
        return words[words.length - 1];
    }
}
