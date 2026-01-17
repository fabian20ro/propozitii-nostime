package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;

/**
 * Provides comparison sentences.
 * Format: [Noun] e mai [adjective] decât [noun].
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
        Adjective adj = getDictionary().getRandomAdjective();
        Noun noun2 = getDictionary().getRandomNoun();

        String adjForm = (noun1.gender() == NounGender.F) ? adj.feminine() : adj.word();
        return noun1.articulated() + " e mai " + adjForm + " decât " + noun2.articulated() + ".";
    }
}
