package scrabble.phrases;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import scrabble.phrases.dictionary.WordDictionary;

/**
 * CDI producer for the word dictionary.
 */
@ApplicationScoped
public class DictionaryProducer {

    @Produces
    @Singleton
    public WordDictionary produceDictionary() throws IOException {
        WordParser parser = new WordParser();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    getClass().getResourceAsStream("/words.txt"),
                    StandardCharsets.UTF_8))) {
            return parser.parse(reader);
        }
    }
}
