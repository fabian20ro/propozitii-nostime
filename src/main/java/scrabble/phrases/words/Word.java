package scrabble.phrases.words;

/**
 * The Class Word.
 */
public class Word {

	/** The name. */
	private String name;

	/** The length. */
	private int length;

	/** The syllables. */
	private int syllables;

	/** The rhyme. */
	private String rhyme;

	/**
	 * Instantiates a new word.
	 *
	 * @param name
	 *            the name
	 */
	public Word(String name) {
		this.name = name;
		this.length = name.length();
		this.syllables = WordUtils.computeSyllableNumber(name);
		this.rhyme = WordUtils.computeRhyme(name);
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the length.
	 *
	 * @return the length
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Gets the syllables.
	 *
	 * @return the syllables
	 */
	public int getSyllables() {
		return syllables;
	}

	/**
	 * Gets the rhyme.
	 *
	 * @return the rhyme
	 */
	public String getRhyme() {
		return rhyme;
	}
}
