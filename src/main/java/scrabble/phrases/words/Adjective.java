package scrabble.phrases.words;

import java.util.Objects;

/**
 * Romanian adjective with feminine form derivation.
 *
 * @param word the adjective in masculine form
 * @param syllables the number of syllables
 * @param rhyme the rhyme suffix (last 3 characters)
 */
public record Adjective(
    String word,
    int syllables,
    String rhyme
) implements Word {

    /**
     * Creates a new Adjective with computed syllables and rhyme.
     *
     * @param word the adjective in masculine form
     */
    public Adjective(String word) {
        this(
            word,
            WordUtils.computeSyllableNumber(word),
            WordUtils.computeRhyme(word)
        );
    }

    /**
     * Compact constructor for validation.
     */
    public Adjective {
        Objects.requireNonNull(word, "word cannot be null");
    }

    /**
     * Gets the feminine form of the adjective.
     *
     * @return the feminine form
     */
    public String feminine() {
        if (word.endsWith("esc")) {
            return word.substring(0, word.length() - 2) + "ască";
        }
        if (word.endsWith("eț")) {
            return word.substring(0, word.length() - 1) + "ață";
        }
        if (word.endsWith("or")) {
            return word.substring(0, word.length() - 1) + "are";
        }
        if (word.endsWith("os")) {
            return word.substring(0, word.length() - 1) + "asă";
        }
        if (word.endsWith("iu")) {
            return word.substring(0, word.length() - 1) + "e";
        }
        if (word.endsWith("ci")) {
            return word.substring(0, word.length() - 1) + "e";
        }
        if (word.endsWith("ru")) {
            return word.substring(0, word.length() - 1) + "ă";
        }
        if (word.endsWith("e") || word.endsWith("o") || word.endsWith("i")) {
            return word;
        }
        return word + "ă";
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

    public String getFeminine() {
        return feminine();
    }
}
