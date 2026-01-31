package scrabble.phrases.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
        // Collect unique nouns and build rhyme groups in one pass
        Set<Noun> seen = new HashSet<>();
        Map<String, List<Noun>> rhymeGroups = new HashMap<>();

        for (int i = 0; i < 200; i++) {
            Noun n = getDictionary().getRandomNoun();
            if (n != null && seen.add(n)) {
                rhymeGroups.computeIfAbsent(n.rhyme(), k -> new ArrayList<>()).add(n);
            }
        }

        List<Noun> allNouns = new ArrayList<>(seen);

        // Find two rhyme groups with at least 2 nouns each
        String rhymeA = null;
        String rhymeB = null;
        for (List<Noun> group : rhymeGroups.values()) {
            if (group.size() >= 2) {
                if (rhymeA == null) {
                    rhymeA = group.getFirst().rhyme();
                } else if (rhymeB == null) {
                    rhymeB = group.getFirst().rhyme();
                    break;
                }
            }
        }

        if (rhymeA == null) {
            // Fallback: just use first nouns available
            nounsA.addAll(allNouns.subList(0, Math.min(2, allNouns.size())));
            nounsB.addAll(allNouns.subList(Math.min(2, allNouns.size()), Math.min(4, allNouns.size())));
            return;
        }

        nounsA.addAll(rhymeGroups.get(rhymeA));

        if (rhymeB == null) {
            // Fallback: use any nouns not in group A
            for (Noun n : allNouns) {
                if (!n.rhyme().equals(rhymeA) && nounsB.size() < 2) {
                    nounsB.add(n);
                }
            }
            return;
        }

        nounsB.addAll(rhymeGroups.get(rhymeB));
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
        if (nouns.isEmpty()) {
            throw new IllegalStateException("No nouns available for mirror line");
        }
        Noun noun = nouns.get(random.nextInt(nouns.size()));
        Adjective adj = getDictionary().getRandomAdjective();
        Verb verb = getDictionary().getRandomVerb();
        if (adj == null || verb == null) {
            throw new IllegalStateException("Dictionary has insufficient words for mirror");
        }

        String adjForm = (noun.gender() == NounGender.F) ? adj.feminine() : adj.word();
        return noun.articulated() + " " + adjForm + " " + verb.word();
    }
}
