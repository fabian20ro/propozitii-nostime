package scrabble.phrases;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Verb;
import scrabble.phrases.words.Word;
import scrabble.phrases.words.WordUtils;

/**
 * The Class WordDictionary.
 */
public class WordDictionary {

	/** The Constant SEED. */
	// private static final long SEED = 0;
	private static final long SEED = System.currentTimeMillis();

	/** The nouns. */
	List<Noun> acceptedNouns = new ArrayList<>();

	/** The refused nouns. */
	List<Noun> refusedNouns = new ArrayList<>();

	/** The adjectives. */
	List<Adjective> acceptedAdjectives = new ArrayList<>();

	/** The refused adjectives. */
	List<Adjective> refusedAdjectives = new ArrayList<>();

	/** The verbs. */
	List<Verb> acceptedVerbs = new ArrayList<>();

	/** The refused verbs. */
	List<Verb> refusedVerbs = new ArrayList<>();

	/** The unknowns. */
	List<String> unknowns = new ArrayList<>();

	/** The random. */
	private Random random = new Random(SEED);

	/** The filters. */
	private List<IWordFilter> filters;

	/**
	 * Instantiates a new word dictionary.
	 */
	public WordDictionary() {
		this(new ArrayList<>());
	}

	/**
	 * Instantiates a new word dictionary.
	 *
	 * @param filters
	 *            the filters
	 */
	public WordDictionary(List<IWordFilter> filters) {
		this.filters = filters;
	}

	/**
	 * Adds the filter.
	 *
	 * @param filter
	 *            the filter
	 */
	public void addFilter(IWordFilter filter) {
		this.filters.add(filter);
		updateAcceptedWordsOnFilterAdded(filter, acceptedNouns, refusedNouns);
		updateAcceptedWordsOnFilterAdded(filter, acceptedAdjectives, refusedAdjectives);
		updateAcceptedWordsOnFilterAdded(filter, acceptedVerbs, refusedVerbs);
	}

	/**
	 * Update accepted words on filter added.
	 *
	 * @param <T>
	 *            the generic type
	 * @param filter
	 *            the filter
	 * @param accepted
	 *            the accepted
	 * @param refused
	 *            the refused
	 */
	private <T extends Word> void updateAcceptedWordsOnFilterAdded(IWordFilter filter, List<T> accepted,
			List<T> refused) {
		List<T> movingWords = new ArrayList<>();
		for (T word : accepted) {
			if (!filter.accepts(word)) {
				movingWords.add(word);
			}
		}
		accepted.removeAll(movingWords);
		refused.addAll(movingWords);
	}

	/**
	 * Clear filters.
	 */
	public void clearFilters() {
		this.filters.clear();
		acceptedNouns.addAll(refusedNouns);
		refusedNouns.clear();
		acceptedAdjectives.addAll(refusedAdjectives);
		refusedAdjectives.clear();
		acceptedVerbs.addAll(refusedVerbs);
		refusedVerbs.clear();
	}

	/**
	 * This operation is more expensive than the addition because the updates
	 * need to go through all filters.
	 *
	 * @param filter
	 *            the filter
	 */
	public void removeFilter(IWordFilter filter) {
		this.filters.remove(filter);
		updateAcceptedWordsOnFilterRemoved(filters, acceptedNouns, refusedNouns);
	}

	/**
	 * Update accepted words on filter removed.
	 *
	 * @param <T>
	 *            the generic type
	 * @param filters
	 *            the filters
	 * @param accepted
	 *            the accepted
	 * @param refused
	 *            the refused
	 */
	private <T extends Word> void updateAcceptedWordsOnFilterRemoved(List<IWordFilter> filters, List<T> accepted,
			List<T> refused) {
		List<T> movingWords = new ArrayList<>();
		nextWord: for (T word : refused) {
			for (IWordFilter filter : filters) {
				if (!filter.accepts(word)) {
					continue nextWord;
				}
			}
			movingWords.add(word);
		}
		accepted.addAll(movingWords);
		refused.removeAll(movingWords);
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
		word = WordUtils.fixWordCharacters(word);
		if (type == null) {
			return;
		}
		if (type.equals("M")) {
			addNoun(new Noun(word, NounGender.MASCULINE));
		} else if (type.equals("F")) {
			addNoun(new Noun(word, NounGender.FEMININE));
		} else if (type.equals("N")) {
			addNoun(new Noun(word, NounGender.NEUTRAL));
		} else if (type.equals("MF")) {
			addNoun(new Noun(word, NounGender.MASCULINE));
			addNoun(new Noun(word, NounGender.FEMININE));
		} else if (type.equals("A")) {
			addAdjective(new Adjective(word));
		} else if (type.equals("VT")) {
			addVerb(new Verb(word));
		} else {
			unknowns.add(word + " : " + type);
		}
	}

	/**
	 * Adds the verb.
	 *
	 * @param verb
	 *            the verb
	 */
	private void addVerb(Verb verb) {
		if (matchesFilters(verb)) {
			acceptedVerbs.add(verb);
		} else {
			refusedVerbs.add(verb);
		}
	}

	/**
	 * Adds the noun.
	 *
	 * @param noun
	 *            the noun
	 */
	private void addNoun(Noun noun) {
		if (matchesFilters(noun)) {
			acceptedNouns.add(noun);
		} else {
			refusedNouns.add(noun);
		}
	}

	/**
	 * Adds the adjective.
	 *
	 * @param adjective
	 *            the adjective
	 */
	private void addAdjective(Adjective adjective) {
		if (matchesFilters(adjective)) {
			acceptedAdjectives.add(adjective);
		} else {
			refusedAdjectives.add(adjective);
		}
	}

	/**
	 * Matches filters.
	 *
	 * @param word
	 *            the word
	 * @return true, if successful
	 */
	private boolean matchesFilters(Word word) {
		for (IWordFilter filter : filters) {
			if (!filter.accepts(word)) {
				return false;
			}
		}
		return true;
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
	public Noun getRandomNoun() {
		return acceptedNouns.isEmpty() ? null : acceptedNouns.get(random.nextInt(acceptedNouns.size()));
	}

	/**
	 * Gets the random adjective.
	 *
	 * @return the random adjective
	 */
	public Adjective getRandomAdjective() {
		return acceptedAdjectives.isEmpty() ? null : acceptedAdjectives.get(random.nextInt(acceptedAdjectives.size()));
	}

	/**
	 * Gets the random verb.
	 *
	 * @return the random verb
	 */
	public Verb getRandomVerb() {
		return acceptedVerbs.isEmpty() ? null : acceptedVerbs.get(random.nextInt(acceptedVerbs.size()));
	}

	/**
	 * Gets the random unknown.
	 *
	 * @return the random unknown
	 */
	public String getRandomUnknown() {
		return unknowns.isEmpty() ? null : unknowns.get(random.nextInt(unknowns.size()));
	}
}
