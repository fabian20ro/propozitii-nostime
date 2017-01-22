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
	 * @param word
	 *            the name
	 * @return the int
	 */
	public static int computeSyllableNumber(String word) {
		int result = 0;
		int length = word.length();
		boolean[] context = new boolean[length];
		for (int i = 0; i < length; i++) {
			context[i] = isVowel(word.charAt(i));
		}
		int currentChar = 0;
		while (currentChar < length) {
			if (context[currentChar]) {
				result++;
				if (currentChar + 1 < length && context[currentChar + 1]) {
					//next is also a vowel. we also include it
					if (currentChar >= 1 && currentChar + 2 < length && !context[currentChar - 1] && !context[currentChar + 2]) {
						//these two vowels are surrounded by consonants so it's actually two syllables, not one
						result++;
						currentChar++;
					}
					currentChar++;
				}
			}
			currentChar++;
		}
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

	private static boolean isVowel(char c) {
		return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'ă' || c == 'â' || c == 'î';
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
