package scrabble.phrases.filters;

import scrabble.phrases.words.Word;

/**
 * The Interface IWordFilter.
 */
public interface IWordFilter {

	/**
	 * Accepts.
	 *
	 * @param word the word
	 * @return true, if successful
	 */
	boolean accepts(Word word);
}
