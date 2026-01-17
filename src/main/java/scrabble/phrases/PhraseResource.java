package scrabble.phrases;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import scrabble.phrases.decorators.DexonlineLinkAdder;
import scrabble.phrases.decorators.FirstSentenceLetterCapitalizer;
import scrabble.phrases.decorators.HtmlVerseBreaker;
import scrabble.phrases.dictionary.WordDictionary;
import scrabble.phrases.providers.FiveWordSentenceProvider;
import scrabble.phrases.providers.HaikuProvider;

/**
 * REST API for generating Romanian sentences.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class PhraseResource {

    @Inject
    WordDictionary baseDictionary;

    private volatile HaikuProvider haiku;
    private volatile FiveWordSentenceProvider fiveWord;

    @PostConstruct
    void init() {
        resetProviders();
    }

    @GET
    @Path("/haiku")
    public SentenceResponse getHaiku() {
        var decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(haiku)
            )
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/five-word")
    public SentenceResponse getFiveWord() {
        var decorated = new DexonlineLinkAdder(
            new FirstSentenceLetterCapitalizer(fiveWord)
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/all")
    public JsonModel getAll() {
        return new JsonModel(getHaiku().sentence(), getFiveWord().sentence());
    }

    @POST
    @Path("/reset")
    public Response reset() {
        resetProviders();
        return Response.ok().build();
    }

    private synchronized void resetProviders() {
        this.haiku = new HaikuProvider(new WordDictionary(baseDictionary));
        this.fiveWord = new FiveWordSentenceProvider(new WordDictionary(baseDictionary));
    }
}
