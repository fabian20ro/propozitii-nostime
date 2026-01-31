package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.filters.IWordFilter;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Word;
import scrabble.phrases.words.WordUtils;

/**
 * Provides haiku-style sentences with 5-7-5 syllable structure.
 */
public class HaikuProvider extends SentenceProvider {

    public HaikuProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        do {
            getDictionary().clearFilters();
            final Noun noun = getDictionary().getRandomNoun();
            getDictionary().addFilter(new IWordFilter() {
                @Override
                public boolean accepts(Word word) {
                    if (word instanceof Noun n) {
                        return WordUtils.computeSyllableNumber(n.articulated()) == 5
                            && noun.rhyme().equals(word.rhyme());
                    }
                    if (word instanceof Adjective) {
                        return word.syllables() == 3 + (noun.gender() == NounGender.F ? 0 : 1);
                    }
                    return word.syllables() == 3;
                }
            });
        } while (getDictionary().getRandomNoun() == null);
    }

    @Override
    public String getSentence() {
        var verb = getDictionary().getRandomVerb();
        var noun = getDictionary().getRandomNoun();
        if (verb == null || noun == null) {
            throw new IllegalStateException("Dictionary has insufficient words for haiku");
        }
        return getNounAdjectiveCombo(getDictionary()).replaceAll(" ", " / ") + " "
            + verb.word() + " / "
            + noun.articulated() + ".";
    }
}
