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

        String line1 = getNounAdjectiveCombo(dict) + " "
            + dict.getRandomVerb().word() + " "
            + dict.getRandomNoun().articulated() + ".";

        String line2 = getNounAdjectiveCombo(dict) + " "
            + dict.getRandomVerb().word() + " "
            + dict.getRandomNoun().articulated() + ".";

        // Capitalize first letter of second line
        String line2Capitalized = Character.toUpperCase(line2.charAt(0)) + line2.substring(1);

        return line1 + " / " + line2Capitalized;
    }
}
