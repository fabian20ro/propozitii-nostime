package scrabble.phrases.words;

/**
 * Sealed interface for all word types in the dictionary.
 */
public sealed interface Word permits Noun, Adjective, Verb {

    /**
     * Gets the word text.
     *
     * @return the word
     */
    String word();

    /**
     * Gets the number of syllables.
     *
     * @return syllable count
     */
    int syllables();

    /**
     * Gets the rhyme suffix (last 3 characters).
     *
     * @return the rhyme
     */
    String rhyme();
}
