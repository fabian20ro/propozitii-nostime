package scrabble.phrases.words;

import java.util.HashMap;

/**
 * The Class WordUtil.
 */
public class WordUtils {

	/** The fixes. */
	static HashMap<String, String> fixes = new HashMap<>();

	/** Diftongi si triftongi. Nu am o idee mai buna. Neologisme nu merg (vezi testele). */
	private static String[] tongs = new String[] { "iai", "eau", "iau", "oai", "ioa", "ia", "oa", "ea", "ua", "âu",
			"ou", "ei", "ai", "oi", "ie", "ui" };

	static {

		// if you don't have utf-8
		// fixes.put("Ã®", "i");
		// fixes.put("Äƒ", "ă");
		// fixes.put("Ã¢", "a");
		// fixes.put("È›", "t");
		// fixes.put("È™", "s");
		fixes.put("'", "");
	}

	/**
	 * Instantiates a new word utils.
	 */
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

	// /**
	// * Compute syllable number. Failed first try for some reason...
	// *
	// * @param word
	// * the name
	// * @return the int
	// */
	// public static int computeSyllableNumber(String word) {
	// int result = 0;
	// int length = word.length();
	// boolean[] context = new boolean[length];
	// for (int i = 0; i < length; i++) {
	// context[i] = isVowel(word.charAt(i));
	// }
	// int currentChar = 0;
	// while (currentChar < length) {
	// if (context[currentChar]) {
	// result++;
	// if (currentChar + 1 < length && context[currentChar + 1]) {
	// //next is also a vowel. we also include it
	// if (currentChar >= 1 && currentChar + 2 < length && !context[currentChar
	// - 1] && !context[currentChar + 2]) {
	// //these two vowels are surrounded by consonants so it's actually two
	// syllables, not one
	// result++;
	// currentChar++;
	// }
	// currentChar++;
	// }
	// }
	// currentChar++;
	// }
	// return result;
	// }

	/**
	 * Compute syllable number.
	 *
	 * @param word
	 *            the word
	 * @return the int
	 */
	public static int computeSyllableNumber(String word) {
		int result = 0;
		char[] chars = word.toCharArray();
		replaceTongsWithChar(chars);
		for (int i =0; i < chars.length; i++) {
			if (isVowel(chars[i])) {
				result++;
			}
		}
		return result;
	}

	/**
	 * Replace tongs.
	 *
	 * @param chars
	 *            the chars
	 */
	private static void replaceTongsWithChar(char[] chars) {
		int clength = chars.length;
		for (String tong : tongs) {
			char[] tch = tong.toCharArray();
			for (int i = 0; i < clength - tong.length() + 1; i++) {
				if (tch.length == 3) {
					if (tch[0] == chars[i] && tch[1] == chars[i + 1] && tch[2] == chars[i + 2]) {
						chars[i] = 'z';
						chars[i + 2] = 'z';
						i += 2;
					}
				} else if (tch[0] == chars[i] && tch[1] == chars[i + 1]) {
					// diftong sau hiat? se pare ca hiat-ul e mai rar...
					if (i > 0) {
						if (!isVowel(chars[i - 1])) {
							chars[i + 1] = 'z';
						} else if (isVowel(chars[i - 1])) {
							chars[i] = 'z';
						}
						i++;
					}
				}
			}
		}
		if (clength >= 2 && isVowel(chars[clength - 1]) && isVowel(chars[clength - 2])
				&& chars[clength - 1] != chars[clength - 2]) {
			chars[clength - 2] = 'z';
		}
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
	 * Checks if is vowel.
	 *
	 * @param c
	 *            the c
	 * @return true, if is vowel
	 */
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
