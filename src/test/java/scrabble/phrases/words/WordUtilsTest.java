package scrabble.phrases.words;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for WordUtils.
 */
class WordUtilsTest {

    @Test
    void shouldComputeCorrectSyllableCount() {
        Map<String, Integer> wordMap = createWordMap(
            "semifinalista 6", "greoi 2", "aalenian 4", "alee 3", "alcool 3", "fiinta 3",
            "puicuta 3", "aeroport 4", "miercureana 4", "policioara 4", "vulpoaica 3", "miau 1", "leoaica 3",
            "lupoaica 3", "mioara 2", "ambiguul 4", "tămâie 3", "bou 1", "reusit 3", "greul 2", "plouat 2",
            "roua 2", "calea 2", "eu 1", "greu 1", "pui 1", "tuiul 2", "ghioc 2"
        );

        for (var entry : wordMap.entrySet()) {
            assertEquals(entry.getValue().intValue(), WordUtils.computeSyllableNumber(entry.getKey()),
                "Syllable count for word: " + entry.getKey());
        }
    }

    @Test
    void shouldCapitalizeFirstLetter() {
        assertEquals("Aloha", WordUtils.capitalizeFirstLeter("aloha"));
        assertEquals("Aloha", WordUtils.capitalizeFirstLeter("Aloha"));
        assertEquals("Înăbușeala", WordUtils.capitalizeFirstLeter("înăbușeala"));
    }

    @Test
    void shouldComputeRhyme() {
        assertEquals("are", WordUtils.computeRhyme("formare"));
        assertEquals("abc", WordUtils.computeRhyme("abc"));
        assertEquals("ab", WordUtils.computeRhyme("ab"));
    }

    private Map<String, Integer> createWordMap(String... entries) {
        Map<String, Integer> wordMap = new HashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split(" ");
            wordMap.put(parts[0], Integer.parseInt(parts[1]));
        }
        return wordMap;
    }
}
