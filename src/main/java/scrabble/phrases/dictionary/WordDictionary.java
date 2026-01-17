package scrabble.phrases.dictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Verb;
import scrabble.phrases.words.Word;
import scrabble.phrases.words.WordUtils;

/**
 * Dictionary of Romanian words organized by type.
 */
public class WordDictionary {

    private final long randomSeed = System.nanoTime();

    private List<Noun> acceptedNouns = new ArrayList<>();
    private List<Noun> refusedNouns = new ArrayList<>();
    private List<Adjective> acceptedAdjectives = new ArrayList<>();
    private List<Adjective> refusedAdjectives = new ArrayList<>();
    private List<Verb> acceptedVerbs = new ArrayList<>();
    private List<Verb> refusedVerbs = new ArrayList<>();
    private List<String> unknowns = new ArrayList<>();

    private Random random = new Random(randomSeed);
    private List<IWordFilter> filters = new ArrayList<>();

    public WordDictionary() {
        this(new ArrayList<>());
    }

    public WordDictionary(WordDictionary dictionary) {
        this.filters = new ArrayList<>(dictionary.filters);
        this.acceptedNouns = new ArrayList<>(dictionary.acceptedNouns);
        this.refusedNouns = new ArrayList<>(dictionary.refusedNouns);
        this.acceptedAdjectives = new ArrayList<>(dictionary.acceptedAdjectives);
        this.refusedAdjectives = new ArrayList<>(dictionary.refusedAdjectives);
        this.acceptedVerbs = new ArrayList<>(dictionary.acceptedVerbs);
        this.refusedVerbs = new ArrayList<>(dictionary.refusedVerbs);
        this.unknowns = new ArrayList<>(dictionary.unknowns);
    }

    public WordDictionary(List<IWordFilter> filters) {
        this.filters = filters;
    }

    public void addFilter(IWordFilter filter) {
        this.filters.add(filter);
        updateAcceptedWordsOnFilterAdded(filter, acceptedNouns, refusedNouns);
        updateAcceptedWordsOnFilterAdded(filter, acceptedAdjectives, refusedAdjectives);
        updateAcceptedWordsOnFilterAdded(filter, acceptedVerbs, refusedVerbs);
    }

    private <T extends Word> void updateAcceptedWordsOnFilterAdded(
            IWordFilter filter, List<T> accepted, List<T> refused) {
        List<T> movingWords = new ArrayList<>();
        for (T word : accepted) {
            if (!filter.accepts(word)) {
                movingWords.add(word);
            }
        }
        accepted.removeAll(movingWords);
        refused.addAll(movingWords);
    }

    public void clearFilters() {
        this.filters.clear();
        acceptedNouns.addAll(refusedNouns);
        refusedNouns.clear();
        acceptedAdjectives.addAll(refusedAdjectives);
        refusedAdjectives.clear();
        acceptedVerbs.addAll(refusedVerbs);
        refusedVerbs.clear();
    }

    public void removeFilter(IWordFilter filter) {
        this.filters.remove(filter);
        updateAcceptedWordsOnFilterRemoved(filters, acceptedNouns, refusedNouns);
        updateAcceptedWordsOnFilterRemoved(filters, acceptedAdjectives, refusedAdjectives);
        updateAcceptedWordsOnFilterRemoved(filters, acceptedVerbs, refusedVerbs);
    }

    private <T extends Word> void updateAcceptedWordsOnFilterRemoved(
            List<IWordFilter> filters, List<T> accepted, List<T> refused) {
        List<T> movingWords = new ArrayList<>();
        nextWord:
        for (T word : refused) {
            for (IWordFilter filter : filters) {
                if (!filter.accepts(word)) {
                    continue nextWord;
                }
            }
            movingWords.add(word);
        }
        accepted.addAll(movingWords);
        refused.removeAll(movingWords);
    }

    public void addWord(String word, String type) {
        word = WordUtils.fixWordCharacters(word);
        if (type == null) {
            return;
        }
        try {
            Integer.parseInt(type);
            int breakIndex = 1;
            if (word.charAt(word.length() - 2) <= 'Z') {
                breakIndex = 2;
            }
            type = word.substring(word.length() - breakIndex);
            word = word.substring(0, word.length() - breakIndex);
        } catch (NumberFormatException e) {
            // nothing to do
        }
        switch (type) {
            case "M" -> addNoun(new Noun(word, NounGender.M));
            case "F" -> addNoun(new Noun(word, NounGender.F));
            case "N" -> addNoun(new Noun(word, NounGender.N));
            case "MF" -> addNoun(new Noun(word, NounGender.M));
            case "A" -> addAdjective(new Adjective(word));
            case "VT", "V" -> addVerb(new Verb(word));
            default -> unknowns.add(word + " : " + type);
        }
    }

    private void addVerb(Verb verb) {
        if (matchesFilters(verb)) {
            acceptedVerbs.add(verb);
        } else {
            refusedVerbs.add(verb);
        }
    }

    private void addNoun(Noun noun) {
        if (matchesFilters(noun)) {
            acceptedNouns.add(noun);
        } else {
            refusedNouns.add(noun);
        }
    }

    private void addAdjective(Adjective adjective) {
        if (matchesFilters(adjective)) {
            acceptedAdjectives.add(adjective);
        } else {
            refusedAdjectives.add(adjective);
        }
    }

    private boolean matchesFilters(Word word) {
        for (IWordFilter filter : filters) {
            if (!filter.accepts(word)) {
                return false;
            }
        }
        return true;
    }

    public Noun getRandomNoun() {
        return acceptedNouns.isEmpty() ? null : acceptedNouns.get(random.nextInt(acceptedNouns.size()));
    }

    public Adjective getRandomAdjective() {
        return acceptedAdjectives.isEmpty() ? null : acceptedAdjectives.get(random.nextInt(acceptedAdjectives.size()));
    }

    public Verb getRandomVerb() {
        return acceptedVerbs.isEmpty() ? null : acceptedVerbs.get(random.nextInt(acceptedVerbs.size()));
    }

    public String getRandomUnknown() {
        return unknowns.isEmpty() ? null : unknowns.get(random.nextInt(unknowns.size()));
    }

    public int getTotalAcceptedWordCount() {
        return acceptedNouns.size() + acceptedAdjectives.size() + acceptedVerbs.size();
    }

    public int getTotalRefusedWordCount() {
        return refusedNouns.size() + refusedAdjectives.size() + refusedVerbs.size();
    }

    public int getTotalUnknownWordCount() {
        return unknowns.size();
    }

    public int getTotalWordCount() {
        return getTotalAcceptedWordCount() + getTotalRefusedWordCount() + getTotalUnknownWordCount();
    }

    public void addFilters(List<IWordFilter> theFilters) {
        for (IWordFilter filter : theFilters) {
            addFilter(filter);
        }
    }
}
