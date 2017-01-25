package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Word;
import scrabble.phrases.words.WordUtils;

/**
 * The Class HaikuProvider.
 */
public class HaikuProvider extends SentenceProvider {

	/**
	 * Instantiates a new haiku provider.
	 *
	 * @param dictionary
	 *            the dictionary
	 */
	public HaikuProvider(WordDictionary dictionary) {
		super(dictionary);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void initFilters() {
		do {
			getDictionary().clearFilters();
			final Noun noun = getDictionary().getRandomNoun();
			getDictionary().addFilter(new IWordFilter() {
				@Override
				public boolean accepts(Word word) {
					if (word instanceof Noun) {
						return WordUtils.computeSyllableNumber(((Noun) word).getArticulated()) == 5
								&& noun.getRhyme().equals(word.getRhyme());
					}
					if (word instanceof Adjective) {
						return word.getSyllables() == 3 + (noun.getGender() == NounGender.FEMININE ? 0 : 1);
					}
					return word.getSyllables() == 3;
				}
			});
		} while (getDictionary().getRandomNoun() == null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see scrabble.phrases.providers.ISentenceProvider#getSentence()
	 */
	@Override
	public String getSentence() {
		return getNounAdjectiveCombo(getDictionary()).replaceAll(" ", " / ") + " "
				+ getDictionary().getRandomVerb().getWord() + " / " + getDictionary().getRandomNoun().getArticulated()
				+ ".";
	}

}
