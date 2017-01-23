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

		//see https://dexonline.ro/articol/5.2._Despărțirea_în_interiorul_cuvintelor
		//for full epicness: 'Normele actuale prevăd despărțirea după pronunțare.'
		Map<String, Integer> wordMap = m();
		wordMap.putAll(m("semifinalista 6", "greoi 2", "aalenian 4", "alee 3", "alcool 3", "fiinta 3",
				"puicuta 3", "aeroport 4", "miercureana 4", "policioara 4", "vulpoaica 3", "miau 1", "leoaica 3",
				"lupoaica 3", "mioara 2", "ambiguul 4", "tămâie 3", "bou 1", "reusit 3", "greul 2", "plouat 2",
				"roua 2", "calea 2", "eu 1", "greu 1", "pui 1", "tuiul 2", "ghioc 2"));
		// not working: "bour 2", "spleen 1", "piui 3", "piuitoare 5", "piuitor 4", "boreal 3"
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
