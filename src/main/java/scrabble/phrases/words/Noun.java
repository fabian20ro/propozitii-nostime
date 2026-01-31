package scrabble.phrases.words;

import java.util.Objects;

/**
 * Romanian noun with gender and articulation support.
 *
 * @param word the noun in base form
 * @param gender the grammatical gender
 * @param syllables the number of syllables
 * @param rhyme the rhyme suffix (last 3 characters)
 */
public record Noun(
    String word,
    NounGender gender,
    int syllables,
    String rhyme
) implements Word {

    /**
     * Creates a new Noun with computed syllables and rhyme.
     *
     * @param word the noun in base form
     * @param gender the grammatical gender
     */
    public Noun(String word, NounGender gender) {
        this(
            word,
            gender,
            WordUtils.computeSyllableNumber(word),
            WordUtils.computeRhyme(word)
        );
    }

    /**
     * Compact constructor for validation.
     */
    public Noun {
        Objects.requireNonNull(word, "word cannot be null");
        Objects.requireNonNull(gender, "gender cannot be null");
    }

    /**
     * Gets the articulated form of the noun.
     *
     * @return the articulated form
     */
    public String articulated() {
        return switch (gender) {
            case M, N -> articulateMasculine();
            case F -> articulateFeminine();
        };
    }

    private String articulateMasculine() {
        if (word.endsWith("u")) {
            return word + "l";
        }
        return word + "ul";
    }

    private String articulateFeminine() {
        if (word.length() > 1 && (word.endsWith("ă") || word.endsWith("ie"))) {
            return word.substring(0, word.length() - 1) + "a";
        }
        if (word.endsWith("a")) {
            return word + "ua";
        }
        return word + "a";
    }

    // Legacy method names for compatibility
    public String getWord() {
        return word;
    }

    public NounGender getGender() {
        return gender;
    }

    public int getSyllables() {
        return syllables;
    }

    public String getRhyme() {
        return rhyme;
    }

    public String getArticulated() {
        return articulated();
    }
}
