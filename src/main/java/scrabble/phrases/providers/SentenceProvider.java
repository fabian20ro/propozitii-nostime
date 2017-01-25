package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;

public abstract class SentenceProvider implements ISentenceProvider {

	private WordDictionary dictionary;

	public SentenceProvider(WordDictionary dictionary) {
		this.dictionary = dictionary;
		initFilters();
	}

	protected abstract void initFilters();
	
	protected WordDictionary getDictionary() {
		return dictionary;
	}
	
	/**
	 * Gets the NA combo.
	 *
	 * @param dictionary
	 *            the dictionary
	 * @return the NA combo
	 */
	protected String getNounAdjectiveCombo(WordDictionary dictionary) {
		Noun randomNoun = dictionary.getRandomNoun();
		Adjective randomAdjective = dictionary.getRandomAdjective();
		return randomNoun.getArticulated() + " " + (NounGender.FEMININE.equals(randomNoun.getGender())
				? randomAdjective.getFeminine() : randomAdjective.getWord());
	}
	
}
