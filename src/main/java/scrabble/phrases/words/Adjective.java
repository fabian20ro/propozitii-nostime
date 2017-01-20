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
			return adjective.substring(0, adjective.length() - 1) + "ața";
		}
		if (adjective.endsWith("or")) {
			return adjective.substring(0, adjective.length() - 1) + "are";
		}
		if (adjective.endsWith("os")) {
			return adjective.substring(0, adjective.length() - 1) + "asă";
		}
		if (adjective.endsWith("iu")) {
			// remuneratoriu
			return adjective.substring(0, adjective.length() - 1) + "e";
		}
		return adjective + "ă";
	}
}
