package scrabble.phrases.providers;

import java.util.ArrayList;
import java.util.List;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Verb;
import scrabble.phrases.words.Word;

/**
 * Provides mirror-style sentences with ABBA rhyme scheme.
 * Four lines of [noun] [adj] [verb], where lines 1&4 rhyme and lines 2&3 rhyme.
 */
public class MirrorProvider extends SentenceProvider {

    private String rhymeA;
    private String rhymeB;

    public MirrorProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        // Find two different rhyme patterns
        do {
            getDictionary().clearFilters();
            Noun noun1 = getDictionary().getRandomNoun();
            Noun noun2 = getDictionary().getRandomNoun();

            // Make sure we have two different rhymes
            while (noun1.rhyme().equals(noun2.rhyme())) {
                noun2 = getDictionary().getRandomNoun();
            }

            rhymeA = noun1.rhyme();
            rhymeB = noun2.rhyme();
        } while (!hasEnoughNounsForBothRhymes());
    }

    private boolean hasEnoughNounsForBothRhymes() {
        int countA = countNounsWithRhyme(rhymeA);
        int countB = countNounsWithRhyme(rhymeB);
        return countA >= 2 && countB >= 2;
    }

    private int countNounsWithRhyme(String rhyme) {
        getDictionary().clearFilters();
        getDictionary().addFilter(new IWordFilter() {
            @Override
            public boolean accepts(Word word) {
                if (word instanceof Noun n) {
                    return rhyme.equals(n.rhyme());
                }
                return true;
            }
        });

        List<Noun> found = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Noun n = getDictionary().getRandomNoun();
            if (n != null && !found.contains(n)) {
                found.add(n);
            }
        }
        return found.size();
    }

    @Override
    public String getSentence() {
        // Line 1: rhyme A
        String line1 = buildLine(rhymeA);
        // Line 2: rhyme B
        String line2 = buildLine(rhymeB);
        // Line 3: rhyme B
        String line3 = buildLine(rhymeB);
        // Line 4: rhyme A
        String line4 = buildLine(rhymeA) + ".";

        return line1 + " / " + line2 + " / " + line3 + " / " + line4;
    }

    private String buildLine(String rhyme) {
        getDictionary().clearFilters();
        getDictionary().addFilter(new IWordFilter() {
            @Override
            public boolean accepts(Word word) {
                if (word instanceof Noun n) {
                    return rhyme.equals(n.rhyme());
                }
                return true;
            }
        });

        Noun noun = getDictionary().getRandomNoun();
        Adjective adj = getDictionary().getRandomAdjective();
        Verb verb = getDictionary().getRandomVerb();

        String adjForm = (noun.gender() == NounGender.F) ? adj.feminine() : adj.word();
        return noun.articulated() + " " + adjForm + " " + verb.word();
    }
}
