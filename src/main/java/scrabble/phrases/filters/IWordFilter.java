package scrabble.phrases.filters;

import scrabble.phrases.words.Word;

public interface IWordFilter {

	boolean accepts(Word word);
}
