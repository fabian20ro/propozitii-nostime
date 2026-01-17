package scrabble.phrases;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import scrabble.phrases.dictionary.WordDictionary;

/**
 * Test helper for loading dictionary in tests.
 */
public final class TestHelper {

    private TestHelper() {}

    public static WordDictionary getPopulatedDictionaryFromIncludedFile() throws IOException {
        BufferedReader reader = getWordStreamReader();
        return new WordParser().parse(reader);
    }

    private static BufferedReader getWordStreamReader() throws FileNotFoundException {
        InputStream in = TestHelper.class.getResourceAsStream("/words.txt");
        if (in == null) {
            in = new FileInputStream(new File("src/main/resources/words.txt"));
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
