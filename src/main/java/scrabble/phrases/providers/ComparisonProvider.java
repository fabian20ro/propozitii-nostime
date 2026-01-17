package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.words.Noun;

/**
 * Provides comparison sentences.
 * Format: [Noun] e mai [adjective] decat [noun].
 */
public class ComparisonProvider extends SentenceProvider {

    public ComparisonProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        // No filters needed
    }

    @Override
    public String getSentence() {
        Noun noun1 = getDictionary().getRandomNoun();
        String adj = getDictionary().getRandomAdjective().word();
        Noun noun2 = getDictionary().getRandomNoun();

        return noun1.articulated() + " e mai " + adj + " decat " + noun2.articulated() + ".";
    }
}
