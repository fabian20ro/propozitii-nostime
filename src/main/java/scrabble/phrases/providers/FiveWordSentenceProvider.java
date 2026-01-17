package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;

/**
 * Provides simple five-word sentences.
 */
public class FiveWordSentenceProvider extends SentenceProvider {

    public FiveWordSentenceProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    public String getSentence() {
        return getNounAdjectiveCombo(getDictionary()) + " "
            + getDictionary().getRandomVerb().word() + " "
            + getNounAdjectiveCombo(getDictionary()) + ".";
    }

    @Override
    protected void initFilters() {
        // no filters needed
    }
}
