package scrabble.phrases;

import static org.junit.Assert.*;

import org.junit.Test;

import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;

// TODO: Auto-generated Javadoc
/**
 * The Class WordDictionaryTest.
 */
public class WordDictionaryTest {

	/**
	 * Test is feminine.
	 */
	@Test
	public void testFeminineNoun() {
		WordDictionary dictionary = new WordDictionary();
		
		dictionary.addWord("macara", "F");
		Noun noun = dictionary.getRandomNoun();
		
		assertEquals("macara", noun.getOriginal());
		assertEquals("macaraua", noun.getArticulated());
		assertNull(noun.getPlural());
		assertNull(noun.getArticulatedPlural());

	}

	/**
	 * Test adjective.
	 */
	@Test
	public void testAdjective() {
		WordDictionary dictionary = new WordDictionary();
		
		dictionary.addWord("frumos", "A");
		Adjective adjective = dictionary.getRandomAdjective();
		
		assertEquals("frumos", adjective.getOriginal());
		assertEquals("frumoasă", adjective.getFeminine());
		assertNull(adjective.getPlural());
		assertNull(adjective.getPluralFeminine());
	}
	
}
