package scrabble.phrases

import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import scrabble.phrases.decorators.DexonlineLinkAdder
import scrabble.phrases.decorators.FirstSentenceLetterCapitalizer
import scrabble.phrases.decorators.HtmlVerseBreaker
import scrabble.phrases.decorators.VerseLineCapitalizer
import scrabble.phrases.providers.*
import scrabble.phrases.repository.WordRepository

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
class PhraseResource {

    @Inject
    lateinit var wordRepository: WordRepository

    @GET
    @Path("/haiku")
    fun getHaiku(): SentenceResponse =
        SentenceResponse(decorateVerse(HaikuProvider(wordRepository)).getSentence())

    @GET
    @Path("/couplet")
    fun getCouplet(): SentenceResponse =
        SentenceResponse(decorateVerse(CoupletProvider(wordRepository)).getSentence())

    @GET
    @Path("/comparison")
    fun getComparison(): SentenceResponse =
        SentenceResponse(decorateSentence(ComparisonProvider(wordRepository)).getSentence())

    @GET
    @Path("/definition")
    fun getDefinition(): SentenceResponse =
        SentenceResponse(DexonlineLinkAdder(DefinitionProvider(wordRepository)).getSentence())

    @GET
    @Path("/tautogram")
    fun getTautogram(): SentenceResponse =
        SentenceResponse(decorateSentence(TautogramProvider(wordRepository)).getSentence())

    @GET
    @Path("/all")
    fun getAll(): AllSentencesResponse = AllSentencesResponse(
        haiku = getHaiku().sentence,
        couplet = getCouplet().sentence,
        comparison = getComparison().sentence,
        definition = getDefinition().sentence,
        tautogram = getTautogram().sentence,
        mirror = getMirror().sentence
    )

    @GET
    @Path("/mirror")
    fun getMirror(): SentenceResponse =
        SentenceResponse(decorateVerse(MirrorProvider(wordRepository)).getSentence())

    private fun decorateVerse(provider: ISentenceProvider): ISentenceProvider =
        HtmlVerseBreaker(DexonlineLinkAdder(VerseLineCapitalizer(provider)))

    private fun decorateSentence(provider: ISentenceProvider): ISentenceProvider =
        DexonlineLinkAdder(FirstSentenceLetterCapitalizer(provider))
}
