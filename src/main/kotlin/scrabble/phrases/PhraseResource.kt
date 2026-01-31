package scrabble.phrases

import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import scrabble.phrases.decorators.DexonlineLinkAdder
import scrabble.phrases.decorators.FirstSentenceLetterCapitalizer
import scrabble.phrases.decorators.HtmlVerseBreaker
import scrabble.phrases.providers.*
import scrabble.phrases.repository.WordRepository

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
class PhraseResource {

    @Inject
    lateinit var wordRepository: WordRepository

    @GET
    @Path("/haiku")
    fun getHaiku(): SentenceResponse {
        val decorated = HtmlVerseBreaker(
            DexonlineLinkAdder(
                FirstSentenceLetterCapitalizer(HaikuProvider(wordRepository))
            )
        )
        return SentenceResponse(decorated.getSentence())
    }

    @GET
    @Path("/couplet")
    fun getCouplet(): SentenceResponse {
        val decorated = HtmlVerseBreaker(
            DexonlineLinkAdder(
                FirstSentenceLetterCapitalizer(CoupletProvider(wordRepository))
            )
        )
        return SentenceResponse(decorated.getSentence())
    }

    @GET
    @Path("/comparison")
    fun getComparison(): SentenceResponse {
        val decorated = DexonlineLinkAdder(
            FirstSentenceLetterCapitalizer(ComparisonProvider(wordRepository))
        )
        return SentenceResponse(decorated.getSentence())
    }

    @GET
    @Path("/definition")
    fun getDefinition(): SentenceResponse {
        val decorated = DexonlineLinkAdder(DefinitionProvider(wordRepository))
        return SentenceResponse(decorated.getSentence())
    }

    @GET
    @Path("/tautogram")
    fun getTautogram(): SentenceResponse {
        val decorated = DexonlineLinkAdder(
            FirstSentenceLetterCapitalizer(TautogramProvider(wordRepository))
        )
        return SentenceResponse(decorated.getSentence())
    }

    @GET
    @Path("/mirror")
    fun getMirror(): SentenceResponse {
        val decorated = HtmlVerseBreaker(
            DexonlineLinkAdder(
                FirstSentenceLetterCapitalizer(MirrorProvider(wordRepository))
            )
        )
        return SentenceResponse(decorated.getSentence())
    }
}
