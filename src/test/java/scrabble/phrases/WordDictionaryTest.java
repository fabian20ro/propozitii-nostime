package scrabble.phrases;

import static org.junit.Assert.*;

import org.junit.Test;

public class WordDictionaryTest {

	@Test
	public void testIsFeminine() {
		WordDictionary dictionary = new WordDictionary();
		assertTrue(dictionary.isFeminine("fata"));
		assertFalse(dictionary.isFeminine("baiatul"));
	}

}
