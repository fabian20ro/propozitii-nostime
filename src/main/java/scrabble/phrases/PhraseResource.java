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
import scrabble.phrases.providers.ComparisonProvider;
import scrabble.phrases.providers.CoupletProvider;
import scrabble.phrases.providers.DefinitionProvider;
import scrabble.phrases.providers.HaikuProvider;
import scrabble.phrases.providers.MirrorProvider;
import scrabble.phrases.providers.TautogramProvider;

/**
 * REST API for generating Romanian sentences.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class PhraseResource {

    @Inject
    WordDictionary baseDictionary;

    private volatile HaikuProvider haiku;
    private volatile CoupletProvider couplet;
    private volatile ComparisonProvider comparison;
    private volatile DefinitionProvider definition;
    private volatile TautogramProvider tautogram;
    private volatile MirrorProvider mirror;

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
    @Path("/couplet")
    public SentenceResponse getCouplet() {
        var decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(couplet)
            )
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/comparison")
    public SentenceResponse getComparison() {
        var decorated = new DexonlineLinkAdder(
            new FirstSentenceLetterCapitalizer(comparison)
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/definition")
    public SentenceResponse getDefinition() {
        var decorated = new DexonlineLinkAdder(definition);
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/tautogram")
    public SentenceResponse getTautogram() {
        var decorated = new DexonlineLinkAdder(
            new FirstSentenceLetterCapitalizer(tautogram)
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/mirror")
    public SentenceResponse getMirror() {
        var decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(mirror)
            )
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @POST
    @Path("/reset")
    public Response reset() {
        resetProviders();
        return Response.ok().build();
    }

    private synchronized void resetProviders() {
        this.haiku = new HaikuProvider(new WordDictionary(baseDictionary));
        this.couplet = new CoupletProvider(new WordDictionary(baseDictionary));
        this.comparison = new ComparisonProvider(new WordDictionary(baseDictionary));
        this.definition = new DefinitionProvider(new WordDictionary(baseDictionary));
        this.tautogram = new TautogramProvider(new WordDictionary(baseDictionary));
        this.mirror = new MirrorProvider(new WordDictionary(baseDictionary));
    }
}
