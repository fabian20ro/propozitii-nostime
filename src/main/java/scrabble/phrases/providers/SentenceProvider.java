package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;

/**
 * Abstract base class for sentence providers.
 */
public abstract class SentenceProvider implements ISentenceProvider {

    private final WordDictionary dictionary;

    protected SentenceProvider(WordDictionary dictionary) {
        this.dictionary = dictionary;
        initFilters();
    }

    protected abstract void initFilters();

    protected WordDictionary getDictionary() {
        return dictionary;
    }

    /**
     * Gets a noun-adjective combination with proper gender agreement.
     *
     * @param dictionary the dictionary to use
     * @return the articulated noun followed by matching adjective
     */
    protected String getNounAdjectiveCombo(WordDictionary dictionary) {
        Noun randomNoun = dictionary.getRandomNoun();
        Adjective randomAdjective = dictionary.getRandomAdjective();
        return randomNoun.articulated() + " " +
            (NounGender.F == randomNoun.gender() ? randomAdjective.feminine() : randomAdjective.word());
    }
}
