package scrabble.phrases.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import scrabble.phrases.TestHelper;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Word;

/**
 * Tests for WordDictionary.
 */
class WordDictionaryTest {

    @Test
    void shouldCopyDictionary() throws IOException {
        WordDictionary dictionary = TestHelper.getPopulatedDictionaryFromIncludedFile();
        WordDictionary secondDictionary = new WordDictionary(dictionary);

        assertEquals(secondDictionary.getTotalWordCount(), dictionary.getTotalWordCount());
        assertEquals(secondDictionary.getTotalAcceptedWordCount(), dictionary.getTotalAcceptedWordCount());
        assertEquals(secondDictionary.getTotalUnknownWordCount(), dictionary.getTotalUnknownWordCount());

        dictionary.addFilter(word -> false);
        assertEquals(secondDictionary.getTotalWordCount(), dictionary.getTotalWordCount());
        assertEquals(secondDictionary.getTotalRefusedWordCount(), dictionary.getTotalAcceptedWordCount());
        assertEquals(secondDictionary.getTotalUnknownWordCount(), dictionary.getTotalUnknownWordCount());
    }

    @Test
    void shouldArticulateNouns() {
        List<String> nouns = List.of(
            "macara F", "fată F", "acar M", "ploaie F", "maestru M", "rodie F", "staul N", "abolitionist MF"
        );
        List<String> articulated = List.of(
            "macaraua", "fata", "acarul", "ploaia", "maestrul", "rodia", "staulul", "abolitionistul", "abolitionista"
        );

        WordDictionary dictionary = new WordDictionary();
        nouns.forEach(word -> dictionary.addWord(
            word.substring(0, word.indexOf(" ")),
            word.substring(word.lastIndexOf(" ") + 1)
        ));

        List<String> words = nouns.stream()
            .map(word -> word.substring(0, word.indexOf(" ")))
            .toList();

        verifyNounsInDictionary(dictionary, words, articulated);
    }

    @Test
    void shouldFeminizeAdjectives() {
        List<String> adjectives = List.of(
            "bor", "frumos", "pitoresc", "zglobiu", "citeț", "stângaci", "alb", "acru", "verde", "maro", "gri"
        );
        List<String> feminines = List.of(
            "boare", "frumoasă", "pitorească", "zglobie", "citeață", "stângace", "albă", "acră", "verde", "maro", "gri"
        );

        WordDictionary dictionary = new WordDictionary();
        adjectives.forEach(word -> dictionary.addWord(word, "A"));

        verifyAdjectivesInDictionary(dictionary, adjectives, feminines);
    }

    @Test
    void shouldManageFilters() {
        IWordFilter lengthFilter = word -> word.word().length() == 6;
        IWordFilter genderFilter = word -> ((Noun) word).gender() == NounGender.F;

        WordDictionary dictionary = new WordDictionary();

        dictionary.addFilter(lengthFilter);
        dictionary.addWord("corabie", "F");
        dictionary.addWord("martor", "M");

        verifyNounsInDictionary(dictionary, List.of("martor"), List.of("martorul"));

        dictionary.clearFilters();
        verifyNounsInDictionary(dictionary, List.of("martor", "corabie"), List.of("martorul", "corabia"));

        dictionary.addFilter(genderFilter);
        verifyNounsInDictionary(dictionary, List.of("corabie"), List.of("corabia"));

        dictionary.removeFilter(genderFilter);
        verifyNounsInDictionary(dictionary, List.of("martor", "corabie"), List.of("martorul", "corabia"));

        dictionary.addFilter(lengthFilter);
        dictionary.addFilter(genderFilter);
        assertNull(dictionary.getRandomNoun());

        dictionary.clearFilters();
        verifyNounsInDictionary(dictionary, List.of("martor", "corabie"), List.of("martorul", "corabia"));

        dictionary.addFilter(word -> word instanceof Adjective);
        assertNull(dictionary.getRandomNoun());
    }

    private void verifyNounsInDictionary(WordDictionary dictionary, List<String> expectedWords,
            List<String> expectedArticulated) {
        int iterations = 10 * expectedWords.size();
        for (int i = 0; i < iterations; i++) {
            Noun noun = dictionary.getRandomNoun();
            assertTrue(expectedWords.contains(noun.word()),
                "Expected word not found: " + noun.word());
            assertTrue(expectedArticulated.contains(noun.articulated()),
                "Expected articulated form not found: " + noun.articulated());
        }
    }

    private void verifyAdjectivesInDictionary(WordDictionary dictionary, List<String> expectedWords,
            List<String> expectedFeminines) {
        int iterations = 10 * expectedWords.size();
        for (int i = 0; i < iterations; i++) {
            Adjective adjective = dictionary.getRandomAdjective();
            assertTrue(expectedWords.contains(adjective.word()),
                "Expected adjective not found: " + adjective.word());
            assertTrue(expectedFeminines.contains(adjective.feminine()),
                "Expected feminine form not found: " + adjective.feminine());
        }
    }
}
