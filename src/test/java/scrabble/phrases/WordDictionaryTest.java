package scrabble.phrases;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * The Class WordDictionaryTest.
 */
public class WordDictionaryTest {

	/**
	 * Test is feminine.
	 */
	@Test
	public void testIsFeminine() {
		WordDictionary dictionary = new WordDictionary();
		assertTrue(dictionary.isFeminine("fata"));
		assertFalse(dictionary.isFeminine("baiatul"));
	}

}
