package scrabble.phrases;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Word;

/**
 * The Class WordDictionaryTest.
 */
public class WordDictionaryTest {

	/**
	 * Test is feminine.
	 */
	@Test
	public void testNouns() {

		// initialize word lists
		List<String> nouns = l("macara F", "fată F", "acar M", "ploaie F", "maestru M", "rodie F", "staul N", "abolitionist MF");
		List<String> articulated = l("macaraua", "fata", "acarul", "ploaia", "maestrul", "rodia", "staulul", "abolitionistul", "abolitionista");

		// populate dictionary
		WordDictionary dictionary = new WordDictionary();
		nouns.stream().forEach(word -> dictionary.addWord(word.substring(0, word.indexOf(" ")),
				word.substring(word.lastIndexOf(" ") + 1)));

		// adapt the words
		List<String> words = nouns.stream().map(word -> word.substring(0, word.indexOf(" ")))
				.collect(Collectors.toList());

		//compare the random outputs
		compareNounWithExpected(dictionary, words, articulated);
	}

	/**
	 * Test adjective.
	 */
	@Test
	public void testAdjective() {

		//initialize word lists
		List<String> adjectives = l("bor", "frumos", "pitoresc", "zglobiu", "citeț", "stângaci", "alb", "acru", "verde", "maro", "gri");
		List<String> feminines = l("boare", "frumoasă", "pitorească", "zglobie", "citeață", "stângace", "albă", "acră", "verde", "maro", "gri");

		//populate dictionary
		WordDictionary dictionary = new WordDictionary();
		adjectives.stream().forEach(word -> dictionary.addWord(word, "A"));

		//compare the random outputs
		compareAdjectiveWithExpected(dictionary, adjectives, feminines);
	}

	/**
	 * Test add, remove and clear filters.
	 */
	@Test
	public void testFilters() {

		//init some filters
		IWordFilter lengthFilter = word -> word.getLength() == 6;
		IWordFilter genderFilter = word -> ((Noun) word).getGender().equals(NounGender.FEMININE);

		WordDictionary dictionary = new WordDictionary();

		dictionary.addFilter(lengthFilter);
		//add words with filters
		dictionary.addWord("corabie", "F");
		dictionary.addWord("martor", "M");

		compareNounWithExpected(dictionary, l("martor"), l("martorul"));

		dictionary.clearFilters();
		compareNounWithExpected(dictionary, l("martor", "corabie"), l("martorul", "corabia"));

		dictionary.addFilter(genderFilter);
		compareNounWithExpected(dictionary, l("corabie"), l("corabia"));

		dictionary.removeFilter(genderFilter);
		compareNounWithExpected(dictionary, l("martor", "corabie"), l("martorul", "corabia"));

		dictionary.addFilter(lengthFilter);
		dictionary.addFilter(genderFilter);
		assertNull(dictionary.getRandomNoun());

		dictionary.clearFilters();
		compareNounWithExpected(dictionary, l("martor", "corabie"), l("martorul", "corabia"));

		dictionary.addFilter(word -> word instanceof Adjective);
		assertNull(dictionary.getRandomNoun());
	}

	/**
	 * Short way to make a list.
	 *
	 * @param <T>
	 *            the generic type
	 * @param t
	 *            the t
	 * @return the list
	 */
	private <T> List<T> l(@SuppressWarnings("unchecked") T... t) {
		return Arrays.asList(t);
	}

	/**
	 * Compare noun with expected.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @param expectedNormal
	 *            the expected normal
	 * @param expectedSecondForm
	 *            the expected second form
	 */
	private void compareNounWithExpected(WordDictionary dictionary, List<String> expectedNormal,
			List<String> expectedSecondForm) {
		compareWithExpected(dictionary, "getRandomNoun", "getArticulated", expectedNormal, expectedSecondForm);
	}

	/**
	 * Compare with expected.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @param randomWordGetter
	 *            the random word getter
	 * @param secondFormGetter
	 *            the second form getter
	 * @param expectedNormal
	 *            the expected normal
	 * @param expectedSecondForm
	 *            the expected second form
	 */
	private void compareWithExpected(WordDictionary dictionary, String randomWordGetter, String secondFormGetter,
			List<String> expectedNormal, List<String> expectedSecondForm) {
		compareWithExpected(dictionary, randomWordGetter, secondFormGetter, expectedNormal, expectedSecondForm,
				10 * expectedNormal.size());
	}

	/**
	 * Invoke getter.
	 *
	 * @param <T>
	 *            the generic type
	 * @param object
	 *            the object
	 * @param methodName
	 *            the method name
	 * @param t
	 *            the t
	 * @return the t
	 */
	@SuppressWarnings("unchecked")
	private <T> T invokeGetter(Object object, String methodName, Class<T> t) {
		try {
			Method method = object.getClass().getMethod(methodName);
			return (T) method.invoke(object);
		} catch (ReflectiveOperationException e) {
			fail(e.getMessage());
		}
		return null;
	}

	/**
	 * Compare with expected.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @param randomWordGetter
	 *            the random word getter
	 * @param secondFormGetter
	 *            the second form getter
	 * @param words
	 *            the words
	 * @param secondForms
	 *            the second forms
	 * @param count
	 *            the count
	 */
	private void compareWithExpected(WordDictionary dictionary, String randomWordGetter, String secondFormGetter,
			final List<String> words, final List<String> secondForms, int count) {
		for (int i = 0; i < count; i++) {
			Word word = (Word) invokeGetter(dictionary, randomWordGetter, Word.class);
			assertTrue(words.contains(word.getWord()));
			String secondForm = invokeGetter(word, secondFormGetter, String.class);
			assertTrue("Didn't find " + secondForm + " in second form list.", secondForms.contains(secondForm));
			// assertNull(noun.getPlural());
			// assertNull(noun.getArticulatedPlural());
		}
	}

	/**
	 * Compare adjective with expected.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @param expectedWords
	 *            the expected words
	 * @param expectedFeminines
	 *            the expected feminines
	 */
	private void compareAdjectiveWithExpected(WordDictionary dictionary, List<String> expectedWords,
			List<String> expectedFeminines) {
		compareWithExpected(dictionary, "getRandomAdjective", "getFeminine", expectedWords, expectedFeminines);
	}

}
