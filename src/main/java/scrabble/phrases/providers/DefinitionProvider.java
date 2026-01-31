package scrabble.phrases.providers;

import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.words.Adjective;
import scrabble.phrases.words.Noun;
import scrabble.phrases.words.NounGender;
import scrabble.phrases.words.Verb;

/**
 * Provides dictionary-style definition sentences.
 * Format: NOUN: [adj] [noun] care [verb].
 */
public class DefinitionProvider extends SentenceProvider {

    public DefinitionProvider(WordDictionary dictionary) {
        super(dictionary);
    }

    @Override
    protected void initFilters() {
        // No filters needed
    }

    @Override
    public String getSentence() {
        Noun defined = getDictionary().getRandomNoun();
        Noun noun = getDictionary().getRandomNoun();
        Adjective adj = getDictionary().getRandomAdjective();
        Verb verb = getDictionary().getRandomVerb();
        if (defined == null || noun == null || adj == null || verb == null) {
            throw new IllegalStateException("Dictionary has insufficient words for definition");
        }

        String adjForm = (noun.gender() == NounGender.F) ? adj.feminine() : adj.word();
        return defined.word().toUpperCase() + ": " + noun.articulated() + " " + adjForm + " care " + verb.word() + ".";
    }
}
