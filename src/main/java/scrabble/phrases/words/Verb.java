package scrabble.phrases.words;

import java.util.Objects;

/**
 * Romanian verb.
 *
 * @param word the verb
 * @param syllables the number of syllables
 * @param rhyme the rhyme suffix (last 3 characters)
 */
public record Verb(
    String word,
    int syllables,
    String rhyme
) implements Word {

    /**
     * Creates a new Verb with computed syllables and rhyme.
     *
     * @param word the verb
     */
    public Verb(String word) {
        this(
            word,
            WordUtils.computeSyllableNumber(word),
            WordUtils.computeRhyme(word)
        );
    }

    /**
     * Compact constructor for validation.
     */
    public Verb {
        Objects.requireNonNull(word, "word cannot be null");
    }

    // Legacy method names for compatibility
    public String getWord() {
        return word;
    }

    public int getSyllables() {
        return syllables;
    }

    public String getRhyme() {
        return rhyme;
    }
}
