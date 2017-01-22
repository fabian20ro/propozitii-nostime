package scrabble.phrases.words;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * The Class WordUtilsTest.
 */
public class WordUtilsTest {

	/**
	 * Test syllables.
	 */
	@Test
	public void testSyllables() {

		Map<String, Integer> wordMap = m("piuitoare 5", "semifinalista 6", "greoi 2");
		// wordMap.putAll(m("piuitor 4", "miercureana 4", "policioara 4",
		// "vulpoaica 3", "miau 1", "leoaica 3", "lupoaica 3"));
		wordMap.putAll(m());

		for (String word : wordMap.keySet()) {
			assertEquals("For word " + word, wordMap.get(word).intValue(), WordUtils.computeSyllableNumber(word));
		}
	}

	/**
	 * Test.
	 */
	@Test
	public void testCapitalization() {
		assertEquals("Aloha", WordUtils.capitalizeFirstLeter("aloha"));
		assertEquals("Aloha", WordUtils.capitalizeFirstLeter("Aloha"));
		assertEquals("țâgâlirea", WordUtils.capitalizeFirstLeter("țâgâlirea"));
	}

	/**
	 * M.
	 *
	 * @param strings
	 *            the strings
	 * @return the map
	 */
	private Map<String, Integer> m(String... strings) {
		Map<String, Integer> wordMap = new HashMap<>();
		for (String string : strings) {
			String[] pieces = string.split(" ");
			wordMap.put(pieces[0], Integer.parseInt(pieces[1]));
		}
		return wordMap;
	}

}
