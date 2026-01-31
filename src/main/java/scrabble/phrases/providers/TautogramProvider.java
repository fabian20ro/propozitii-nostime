package scrabble.phrases.providers;

import java.util.Random;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Word;

/**
 * Provides tautogram sentences where all words start with the same letter.
 * Format: [noun] [adj] [verb] [noun].
 */
public class TautogramProvider extends SentenceProvider {

    private static final String LETTERS = "abcdefghilmnoprstuvz";
    private static final Random random = new Random();

    public TautogramProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        int attempts = 0;
        do {
            getDictionary().clearFilters();
            char targetLetter = LETTERS.charAt(random.nextInt(LETTERS.length()));
            final char letter = targetLetter;
            getDictionary().addFilter(new IWordFilter() {
                @Override
                public boolean accepts(Word word) {
                    return Character.toLowerCase(word.word().charAt(0)) == letter;
                }
            });
            attempts++;
        } while (!hasEnoughWords() && attempts < 100);
    }

    private boolean hasEnoughWords() {
        return getDictionary().getRandomNoun() != null
            && getDictionary().getRandomAdjective() != null
            && getDictionary().getRandomVerb() != null;
    }

    @Override
    public String getSentence() {
        var verb = getDictionary().getRandomVerb();
        var noun = getDictionary().getRandomNoun();
        if (verb == null || noun == null) {
            throw new IllegalStateException("Dictionary has insufficient words for tautogram");
        }
        return getNounAdjectiveCombo(getDictionary()) + " "
            + verb.word() + " "
            + noun.articulated() + ".";
    }
}
