package scrabble.phrases.words;

import java.util.HashMap;

/**
 * The Class WordUtil.
 */
public class WordUtils {

	/** The fixes. */
	static HashMap<String, String> fixes = new HashMap<>();

	static {

		// if you don't have utf-8
		// fixes.put("Ã®", "i");
		// fixes.put("Äƒ", "ă");
		// fixes.put("Ã¢", "a");
		// fixes.put("È›", "t");
		// fixes.put("È™", "s");
		fixes.put("'", "");
	}

	private WordUtils() {
		
	}
	
	/**
	 * Capitalize first leter.
	 *
	 * @param sentence
	 *            the sentence
	 * @return the string
	 */
	public static String capitalizeFirstLeter(String sentence) {
		char firstChar = sentence.charAt(0);
		if (firstChar >= 'a' && firstChar <= 'z') {
			sentence = ("" + (char) (firstChar - 32)) + sentence.substring(1);
		}
		return sentence;
	}
	
	/**
	 * Compute syllable number.
	 *
	 * @param name
	 *            the name
	 * @return the int
	 */
	public static int computeSyllableNumber(String name) {
		int result = 1;
		// TODO - implement me
		return result;
	}

	/**
	 * Compute rhyme.
	 *
	 * @param name
	 *            the name
	 * @return the string
	 */
	public static String computeRhyme(String name) {
		return name.substring(Math.max(0, name.length() - 3));
	}

	/**
	 * Fix word characters.
	 *
	 * @param word
	 *            the word
	 * @return the string
	 */
	public static String fixWordCharacters(String word) {
		for (String fix : fixes.keySet()) {
			word = word.replace(fix, fixes.get(fix));
		}
		return word;
	}
}
