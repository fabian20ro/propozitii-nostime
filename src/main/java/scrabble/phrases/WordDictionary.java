package scrabble.phrases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

// TODO: Auto-generated Javadoc
/**
 * The Class WordDictionary.
 */
public class WordDictionary {

	/** The Constant NOUN_IDS. */
	private static final List<String> NOUN_IDS = new ArrayList<>();

	/** The Constant ADJECTIVE_IDS. */
	private static final List<String> ADJECTIVE_IDS = Arrays.asList(new String[] { "A" });

	/** The Constant VERB_IDS. */
	private static final List<String> VERB_IDS = Arrays.asList(new String[] { "VT" }); // ,
																						// "V"

	/** The Constant SEED. */
	// private static final long SEED = 0;
	private static final long SEED = System.currentTimeMillis();

	/** The nouns. */
	List<String> nouns = new ArrayList<>();

	/** The adjectives. */
	List<String> adjectives = new ArrayList<>();

	/** The verbs. */
	List<String> verbs = new ArrayList<>();

	/** The unknowns. */
	List<String> unknowns = new ArrayList<>();

	/** The random. */
	private Random random = new Random(SEED);

	/** The fixes. */
	static HashMap<String, String> fixes = new HashMap<>();

	static {

		NOUN_IDS.addAll(Arrays.asList(new String[] { "MF", "M", "N" }));
		NOUN_IDS.addAll(Arrays.asList(new String[] { "F" }));

		// if you don't have utf-8
		// fixes.put("Ã®", "i");
		// fixes.put("Äƒ", "ă");
		// fixes.put("Ã¢", "a");
		// fixes.put("È›", "t");
		// fixes.put("È™", "s");
		fixes.put("'", "");
	}

	/**
	 * Adds the word.
	 *
	 * @param word
	 *            the word
	 * @param type
	 *            the type
	 */
	public void addWord(String word, String type) {
		word = fixWordCharacters(word);
		if (NOUN_IDS.contains(type)) {
			nouns.add(fixNoun(word, type));
		} else if (ADJECTIVE_IDS.contains(type)) {
			adjectives.add(word);
		} else if (VERB_IDS.contains(type)) {
			verbs.add(word);
		} else {
			unknowns.add(word + " : " + type);
		}
	}

	/**
	 * Fix noun.
	 *
	 * @param word
	 *            the word
	 * @param type
	 *            the type
	 * @return the string
	 */
	private String fixNoun(String word, String type) {
		if ("M".equals(type) || "N".equals(type) || "MF".equals(type)) {
			return masculinizeNoun(word);
		} else if ("F".equals(type)) {
			return feminizeNoun(word);
		}
		return "<" + word + ">";
	}

	/**
	 * Masculinize noun.
	 *
	 * @param word
	 *            the word
	 * @return the string
	 */
	private String masculinizeNoun(String word) {
		if (word.endsWith("u")) {
			return word + "l";
		} else {
			return word + "ul";
		}
	}

	/**
	 * Feminize noun.
	 *
	 * @param word
	 *            the word
	 * @return the string
	 */
	private String feminizeNoun(String word) {
		if (word.endsWith("ă") || word.endsWith("ie")) {
			return word.substring(0, word.length() - 1) + "a";
		}
		if (word.endsWith("a")) {
			return word + "ua";
		}
		return word + "a";
	}

	/**
	 * Fix word characters.
	 *
	 * @param word
	 *            the word
	 * @return the string
	 */
	private String fixWordCharacters(String word) {
		for (String fix : fixes.keySet()) {
			word = word.replace(fix, fixes.get(fix));
		}
		return word;
	}

	/**
	 * Checks if is feminine.
	 *
	 * @param word
	 *            the word
	 * @return true, if is feminine
	 */
	public boolean isFeminine(String word) {
		return !word.endsWith("l");
	}

	/**
	 * Gets the random noun.
	 *
	 * @return the random noun
	 */
	public String getRandomNoun() {
		return nouns.get(random.nextInt(nouns.size()));
	}

	/**
	 * Gets the random adjective.
	 *
	 * @param feminine
	 *            the feminine
	 * @return the random adjective
	 */
	public String getRandomAdjective(boolean feminine) {
		String adjective = adjectives.get(random.nextInt(adjectives.size()));
		if (feminine) {
			return feminizeAdjective(adjective);
		}
		return adjective;
	}

	/**
	 * Feminize adjective.
	 *
	 * @param adjective
	 *            the adjective
	 * @return the string
	 */
	private String feminizeAdjective(String adjective) {
		if (adjective.endsWith("esc")) {
			return adjective.substring(0, adjective.length() - 2) + "ască";
		}
		if (adjective.endsWith("eț")) {
			return adjective.substring(0, adjective.length() - 1) + "ața";
		}
		if (adjective.endsWith("or")) {
			return adjective.substring(0, adjective.length() - 1) + "are";
		}
		if (adjective.endsWith("os")) {
			return adjective.substring(0, adjective.length() - 1) + "asă";
		}
		if (adjective.endsWith("iu")) {
			// remuneratoriu
			return adjective.substring(0, adjective.length() - 1) + "e";
		}
		return adjective + "ă";
	}

	/**
	 * Gets the random verb.
	 *
	 * @return the random verb
	 */
	public String getRandomVerb() {
		return verbs.get(random.nextInt(verbs.size()));
	}

	/**
	 * Gets the random unknown.
	 *
	 * @return the random unknown
	 */
	public String getRandomUnknown() {
		return unknowns.get(random.nextInt(unknowns.size()));
	}
}
