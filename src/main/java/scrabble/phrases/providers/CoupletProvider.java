package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.Word;

/**
 * Provides couplet-style sentences with two rhyming lines.
 * Each line follows the pattern: [noun adj] [verb] [noun]
 */
public class CoupletProvider extends SentenceProvider {

    public CoupletProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        do {
            getDictionary().clearFilters();
            final Noun rhymeNoun = getDictionary().getRandomNoun();
            getDictionary().addFilter(new IWordFilter() {
                @Override
                public boolean accepts(Word word) {
                    if (word instanceof Noun n) {
                        return rhymeNoun.rhyme().equals(n.rhyme());
                    }
                    return true;
                }
            });
        } while (countAvailableNouns() < 4);
    }

    private int countAvailableNouns() {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            if (getDictionary().getRandomNoun() != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String getSentence() {
        WordDictionary dict = getDictionary();

        var verb1 = dict.getRandomVerb();
        var noun1 = dict.getRandomNoun();
        var verb2 = dict.getRandomVerb();
        var noun2 = dict.getRandomNoun();
        if (verb1 == null || noun1 == null || verb2 == null || noun2 == null) {
            throw new IllegalStateException("Dictionary has insufficient words for couplet");
        }

        String line1 = getNounAdjectiveCombo(dict) + " "
            + verb1.word() + " "
            + noun1.articulated() + ".";

        String line2 = getNounAdjectiveCombo(dict) + " "
            + verb2.word() + " "
            + noun2.articulated() + ".";

        // Capitalize first letter of second line
        String line2Capitalized = line2.isEmpty() ? line2
            : Character.toUpperCase(line2.charAt(0)) + line2.substring(1);

        return line1 + " / " + line2Capitalized;
    }
}
