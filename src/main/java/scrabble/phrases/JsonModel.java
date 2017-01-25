package scrabble.phrases;

/**
 * The Class JsonModel. private fields are needed for inclusion into json.
 */
public class JsonModel {

	/** The haiku. */
	private String haiku;

	/** The five word sentence. */
	private String fiveWordSentence;

	/**
	 * Instantiates a new json model.
	 *
	 * @param haiku
	 *            the haiku
	 * @param fiveWord
	 *            the five word
	 */
	public JsonModel(String haiku, String fiveWord) {
		this.haiku = haiku;
		this.fiveWordSentence = fiveWord;
	}

}
