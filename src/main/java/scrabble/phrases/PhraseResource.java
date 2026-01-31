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

    private record Providers(
        HaikuProvider haiku,
        CoupletProvider couplet,
        ComparisonProvider comparison,
        DefinitionProvider definition,
        TautogramProvider tautogram,
        MirrorProvider mirror
    ) {}

    private volatile Providers providers;

    @PostConstruct
    void init() {
        resetProviders();
    }

    @GET
    @Path("/haiku")
    public SentenceResponse getHaiku() {
        var p = providers;
        var decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(p.haiku)
            )
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/couplet")
    public SentenceResponse getCouplet() {
        var p = providers;
        var decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(p.couplet)
            )
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/comparison")
    public SentenceResponse getComparison() {
        var p = providers;
        var decorated = new DexonlineLinkAdder(
            new FirstSentenceLetterCapitalizer(p.comparison)
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/definition")
    public SentenceResponse getDefinition() {
        var p = providers;
        var decorated = new DexonlineLinkAdder(p.definition);
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/tautogram")
    public SentenceResponse getTautogram() {
        var p = providers;
        var decorated = new DexonlineLinkAdder(
            new FirstSentenceLetterCapitalizer(p.tautogram)
        );
        return new SentenceResponse(decorated.getSentence());
    }

    @GET
    @Path("/mirror")
    public SentenceResponse getMirror() {
        var p = providers;
        var decorated = new HtmlVerseBreaker(
            new DexonlineLinkAdder(
                new FirstSentenceLetterCapitalizer(p.mirror)
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

    private void resetProviders() {
        this.providers = new Providers(
            new HaikuProvider(new WordDictionary(baseDictionary)),
            new CoupletProvider(new WordDictionary(baseDictionary)),
            new ComparisonProvider(new WordDictionary(baseDictionary)),
            new DefinitionProvider(new WordDictionary(baseDictionary)),
            new TautogramProvider(new WordDictionary(baseDictionary)),
            new MirrorProvider(new WordDictionary(baseDictionary))
        );
    }
}
