package scrabble.phrases.words;

/**
 * The Class Adjective.
 */
public class Adjective extends Word {

	/** The feminine. */
	private String feminine;

	/** The plural. */
	private String plural;

	/** The plural feminine. */
	private String pluralFeminine;

	/**
	 * Instantiates a new adjective.
	 *
	 * @param word
	 *            the word
	 */
	public Adjective(String word) {
		super(word);
		this.feminine = feminizeAdjective(word);
	}

	/**
	 * Gets the feminine.
	 *
	 * @return the feminine
	 */
	public String getFeminine() {
		return feminine;
	}

	/**
	 * Gets the plural.
	 *
	 * @return the plural
	 */
	public String getPlural() {
		return plural;
	}

	/**
	 * Gets the plural feminine.
	 *
	 * @return the plural feminine
	 */
	public String getPluralFeminine() {
		return pluralFeminine;
	}

	/**
	 * Feminize adjective.
	 *
	 * @param adjective
	 *            the adjective
	 * @return the string
	 */
	private String feminizeAdjective(String adjective) {
		if (adjective.endsWith("esc")) {
			return adjective.substring(0, adjective.length() - 2) + "ască";
		}
		if (adjective.endsWith("eț")) {
			return adjective.substring(0, adjective.length() - 1) + "ață";
		}
		if (adjective.endsWith("or")) {
			return adjective.substring(0, adjective.length() - 1) + "are";
		}
		if (adjective.endsWith("os")) {
			return adjective.substring(0, adjective.length() - 1) + "asă";
		}
		if (adjective.endsWith("iu")) {
			return adjective.substring(0, adjective.length() - 1) + "e";
		}
		if (adjective.endsWith("ci")) {
			return adjective.substring(0, adjective.length() - 1) + "e";
		}
		if (adjective.endsWith("ru")) {
			return adjective.substring(0, adjective.length() - 1) + "ă";
		}
		if (adjective.endsWith("e") || adjective.endsWith("o") || adjective.endsWith("i")) {
			return adjective;
		}
		return adjective + "ă";
	}
}
