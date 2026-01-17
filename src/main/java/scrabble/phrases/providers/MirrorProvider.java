package scrabble.phrases.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Verb;

/**
 * Provides mirror-style sentences with ABBA rhyme scheme.
 * Four lines of [noun] [adj] [verb], where lines 1&4 rhyme and lines 2&3 rhyme.
 */
public class MirrorProvider extends SentenceProvider {

    private static final Random random = new Random();
    private List<Noun> nounsA;
    private List<Noun> nounsB;

    public MirrorProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        // Initialize lists here since initFilters is called from super constructor
        nounsA = new ArrayList<>();
        nounsB = new ArrayList<>();
        collectRhymingNouns();
    }

    private void collectRhymingNouns() {
        // Collect many nouns and group by rhyme
        List<Noun> allNouns = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Noun n = getDictionary().getRandomNoun();
            if (n != null && !allNouns.contains(n)) {
                allNouns.add(n);
            }
        }

        // Find first rhyme group with at least 2 nouns
        String rhymeA = null;
        for (Noun n : allNouns) {
            long count = allNouns.stream().filter(x -> x.rhyme().equals(n.rhyme())).count();
            if (count >= 2) {
                rhymeA = n.rhyme();
                break;
            }
        }

        if (rhymeA == null) {
            // Fallback: just use first two nouns
            nounsA.addAll(allNouns.subList(0, Math.min(2, allNouns.size())));
            nounsB.addAll(allNouns.subList(Math.min(2, allNouns.size()), Math.min(4, allNouns.size())));
            return;
        }

        // Find second rhyme group different from first
        String rhymeB = null;
        final String finalRhymeA = rhymeA;
        for (Noun n : allNouns) {
            if (!n.rhyme().equals(finalRhymeA)) {
                long count = allNouns.stream().filter(x -> x.rhyme().equals(n.rhyme())).count();
                if (count >= 2) {
                    rhymeB = n.rhyme();
                    break;
                }
            }
        }

        if (rhymeB == null) {
            // Fallback: use any nouns not in group A
            for (Noun n : allNouns) {
                if (n.rhyme().equals(rhymeA)) {
                    nounsA.add(n);
                } else if (nounsB.size() < 2) {
                    nounsB.add(n);
                }
            }
            return;
        }

        // Collect nouns for both groups
        final String finalRhymeB = rhymeB;
        for (Noun n : allNouns) {
            if (n.rhyme().equals(finalRhymeA)) {
                nounsA.add(n);
            } else if (n.rhyme().equals(finalRhymeB)) {
                nounsB.add(n);
            }
        }
    }

    @Override
    public String getSentence() {
        // Line 1: rhyme A
        String line1 = buildLine(nounsA) + ",";
        // Line 2: rhyme B
        String line2 = buildLine(nounsB) + ",";
        // Line 3: rhyme B
        String line3 = buildLine(nounsB) + ",";
        // Line 4: rhyme A
        String line4 = buildLine(nounsA) + ".";

        return line1 + " / " + line2 + " / " + line3 + " / " + line4;
    }

    private String buildLine(List<Noun> nouns) {
        Noun noun = nouns.get(random.nextInt(nouns.size()));
        Adjective adj = getDictionary().getRandomAdjective();
        Verb verb = getDictionary().getRandomVerb();

        String adjForm = (noun.gender() == NounGender.F) ? adj.feminine() : adj.word();
        return noun.articulated() + " " + adjForm + " " + verb.word();
    }
}
