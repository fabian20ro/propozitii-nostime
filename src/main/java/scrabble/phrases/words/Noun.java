package scrabble.phrases.words;

// TODO: Auto-generated Javadoc
/**
 * The Class Noun.
 */
public class Noun extends Word {

	/** The gender. */
	private NounGender gender;

	/** The articulated form. */
	private String articulatedForm;

	/** The plural articulated. */
	private String pluralArticulated;

	/** The plural. */
	private String plural;

	/**
	 * Instantiates a new noun.
	 *
	 * @param word
	 *            the word
	 * @param gender
	 *            the gender
	 */
	public Noun(String word, NounGender gender) {
		super(word);
		this.gender = gender;
		switch (gender) {
		case FEMININE:
			articulatedForm = articulateFeminineSingularNoun(word);
			break;
		case MASCULINE:
			articulatedForm = articulateMasculineNoun(word);
			break;
		case NEUTRAL:
			articulatedForm = articulateMasculineNoun(word);
			break;
		}
	}

	/**
	 * Masculinize noun.
	 *
	 * @param word
	 *            the word
	 * @return the string
	 */
	private String articulateMasculineNoun(String word) {
		if (word.endsWith("u")) {
			return word + "l";
		} else {
			return word + "ul";
		}
	}

	/**
	 * Feminize noun.
	 *
	 * @param word
	 *            the word
	 * @return the string
	 */
	private String articulateFeminineSingularNoun(String word) {
		if (word.endsWith("ă") || word.endsWith("ie")) {
			return word.substring(0, word.length() - 1) + "a";
		}
		if (word.endsWith("a")) {
			return word + "ua";
		}
		return word + "a";
	}

	/**
	 * Gets the gender.
	 *
	 * @return the gender
	 */
	public NounGender getGender() {
		return gender;
	}

	/**
	 * Gets the articulated form.
	 *
	 * @return the articulated form
	 */
	public String getArticulatedForm() {
		return this.articulatedForm;
	}

	/**
	 * Gets the plural.
	 *
	 * @return the plural
	 */
	public String getPlural() {
		return this.plural;
	}

	/**
	 * Gets the articulated plural form.
	 *
	 * @return the articulated plural form
	 */
	public String getArticulatedPluralForm() {
		return this.pluralArticulated;
	}
}
