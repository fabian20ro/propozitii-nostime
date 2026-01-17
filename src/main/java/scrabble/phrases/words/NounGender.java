package scrabble.phrases.words;

/**
 * Romanian noun gender.
 */
public enum NounGender {

    /** Masculine gender. */
    M,

    /** Feminine gender. */
    F,

    /** Neutral gender (masculine singular, feminine plural). */
    N;

    /**
     * Parse gender from dictionary format.
     *
     * @param s the gender string (m, f, n, or legacy MASCULINE, FEMININE, NEUTRAL)
     * @return the gender
     */
    public static NounGender fromString(String s) {
        return switch (s.toLowerCase()) {
            case "m", "masculine" -> M;
            case "f", "feminine" -> F;
            case "n", "neutral" -> N;
            default -> throw new IllegalArgumentException("Unknown gender: " + s);
        };
    }
}
