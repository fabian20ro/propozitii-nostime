package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;

public class FiveWordSentenceProvider extends SentenceProvider {

	public FiveWordSentenceProvider(WordDictionary dictionary) {
		super(dictionary);
	}

	@Override
	public String getSentence() {
		return getNounAdjectiveCombo(getDictionary()) + " " + getDictionary().getRandomVerb().getWord() + " "
				+ getNounAdjectiveCombo(getDictionary()) + ".";
	}

	@Override
	protected void initFilters() {
		// nothing
	}

}
