package scrabble.phrases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;

/**
 * The Class WordParserTest.
 */
public class WordParserTest {

	private IWordFilter refuseAll = word -> false;
	private IWordFilter wordsWithMoreThan10Chars = word -> word.getLength() > 10;

	
	/**
	 * This test works but it takes too long to be run after each commit!.
	 *
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@Test
	@Ignore
	public void test() throws IOException {

		long t1 = System.currentTimeMillis();

		WordDictionary dictionary = new Main().getPopulatedDictionaryFromIncludedFile();

		long t2 = System.currentTimeMillis();

		int accepted = dictionary.getTotalAcceptedWordCount();
		int refused = dictionary.getTotalRefusedWordCount();
		int unknown = dictionary.getTotalUnknownWordCount();
		int total = dictionary.getTotalWordCount();
		System.out.println(
				"accepted: " + accepted + "\nrefused: " + refused + "\nunknown: " + unknown + "\ntotal: " + total);
		assertNotNull(dictionary.getRandomNoun());
		assertNotNull(dictionary.getRandomAdjective());
		assertNotNull(dictionary.getRandomVerb());

		dictionary.addFilter(refuseAll);
		assertEquals(accepted, dictionary.getTotalRefusedWordCount());
		assertNull(dictionary.getRandomNoun());
		assertNull(dictionary.getRandomAdjective());
		assertNull(dictionary.getRandomVerb());
		assertEquals(total, dictionary.getTotalWordCount());

		long t3 = System.currentTimeMillis();

		dictionary.removeFilter(refuseAll);
		assertEquals(accepted, dictionary.getTotalAcceptedWordCount());
		assertNotNull(dictionary.getRandomNoun());
		assertNotNull(dictionary.getRandomAdjective());
		assertNotNull(dictionary.getRandomVerb());
		assertEquals(total, dictionary.getTotalWordCount());

		long t4 = System.currentTimeMillis();

		dictionary.addFilter(refuseAll); // TODO see why this second filter
											// adition takes so much more
											// compared to the first one
		assertEquals(accepted, dictionary.getTotalRefusedWordCount());
		assertNull(dictionary.getRandomNoun());
		assertNull(dictionary.getRandomAdjective());
		assertNull(dictionary.getRandomVerb());
		assertEquals(total, dictionary.getTotalWordCount());

		long t5 = System.currentTimeMillis();

		dictionary.clearFilters();
		assertNotNull(dictionary.getRandomNoun());
		assertNotNull(dictionary.getRandomAdjective());
		assertNotNull(dictionary.getRandomVerb());
		assertEquals(total, dictionary.getTotalWordCount());

		long t6 = System.currentTimeMillis();

		dictionary.addFilter(wordsWithMoreThan10Chars);
		assertEquals(total, dictionary.getTotalWordCount());

		long t7 = System.currentTimeMillis();

		dictionary.removeFilter(wordsWithMoreThan10Chars);
		assertEquals(total, dictionary.getTotalWordCount());
		assertEquals(accepted, dictionary.getTotalAcceptedWordCount());

		long t8 = System.currentTimeMillis();

		System.out.println("Initial dictionary population took: " + (t2 - t1) + " millis.");
		System.out.println("Adding filter to remove them all took: " + (t3 - t2) + " millis.");
		System.out.println("Removing filter to add them all back took: " + (t4 - t3) + " millis.");
		System.out.println("Adding filter to remove them all again took: " + (t5 - t4) + " millis.");
		System.out.println("Clearing all filters took: " + (t6 - t5) + " millis.");
		System.out.println("Adding filter to remove all words with less than 11 characters: " + (t7 - t6) + " millis.");
		System.out.println(
				"Removing filter that removes all words with less than 11 characters: " + (t8 - t7) + " millis.");
		System.out.println("Overall, it took: " + (t8 - t1) + " millis.");
	}

}
