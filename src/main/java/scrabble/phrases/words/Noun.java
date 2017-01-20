package scrabble.phrases.words;

public class Noun extends Word {

	private NounType gender;
	private String articulatedForm;
	private String pluralArticulated;
	private String plural;

	public Noun(String word, NounType gender) {
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
	
	public NounType getGender() {
		return gender;
	}

	public String getArticulatedForm() {
		return this.articulatedForm;
	}
	
	public String getPlural() {
		return this.plural;
	}
	
	public String getArticulatedPluralForm() {
		return this.pluralArticulated;
	}
}
