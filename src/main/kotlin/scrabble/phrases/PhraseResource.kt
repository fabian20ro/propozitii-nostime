package scrabble.phrases

import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import scrabble.phrases.decorators.DexonlineLinkAdder
import scrabble.phrases.decorators.FirstSentenceLetterCapitalizer
import scrabble.phrases.decorators.HtmlVerseBreaker
import scrabble.phrases.decorators.VerseLineCapitalizer
import scrabble.phrases.providers.ComparisonProvider
import scrabble.phrases.providers.CoupletProvider
import scrabble.phrases.providers.DefinitionProvider
import scrabble.phrases.providers.HaikuProvider
import scrabble.phrases.providers.ISentenceProvider
import scrabble.phrases.providers.MirrorProvider
import scrabble.phrases.providers.TautogramProvider
import scrabble.phrases.repository.WordRepository

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
class PhraseResource {

    @Inject
    lateinit var wordRepository: WordRepository

    @GET
    @Path("/haiku")
    fun getHaiku(@QueryParam("strangeness") strangeness: Int?): SentenceResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return SentenceResponse(safeGenerate { decorateVerse(HaikuProvider(wordRepository, maxRarity)).getSentence() })
    }

    @GET
    @Path("/couplet")
    fun getCouplet(@QueryParam("strangeness") strangeness: Int?): SentenceResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return SentenceResponse(safeGenerate { decorateVerse(CoupletProvider(wordRepository, maxRarity)).getSentence() })
    }

    @GET
    @Path("/comparison")
    fun getComparison(@QueryParam("strangeness") strangeness: Int?): SentenceResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return SentenceResponse(safeGenerate { decorateSentence(ComparisonProvider(wordRepository, maxRarity)).getSentence() })
    }

    @GET
    @Path("/definition")
    fun getDefinition(@QueryParam("strangeness") strangeness: Int?): SentenceResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return SentenceResponse(safeGenerate { DexonlineLinkAdder(DefinitionProvider(wordRepository, maxRarity)).getSentence() })
    }

    @GET
    @Path("/tautogram")
    fun getTautogram(@QueryParam("strangeness") strangeness: Int?): SentenceResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return SentenceResponse(safeGenerate { decorateSentence(TautogramProvider(wordRepository, maxRarity)).getSentence() })
    }

    @GET
    @Path("/all")
    fun getAll(@QueryParam("strangeness") strangeness: Int?): AllSentencesResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return AllSentencesResponse(
            haiku = getHaiku(maxRarity).sentence,
            couplet = getCouplet(maxRarity).sentence,
            comparison = getComparison(maxRarity).sentence,
            definition = getDefinition(maxRarity).sentence,
            tautogram = getTautogram(maxRarity).sentence,
            mirror = getMirror(maxRarity).sentence
        )
    }

    @GET
    @Path("/mirror")
    fun getMirror(@QueryParam("strangeness") strangeness: Int?): SentenceResponse {
        val maxRarity = normalizeStrangeness(strangeness)
        return SentenceResponse(safeGenerate { decorateVerse(MirrorProvider(wordRepository, maxRarity)).getSentence() })
    }

    private fun safeGenerate(generator: () -> String): String =
        try {
            generator()
        } catch (_: IllegalStateException) {
            UNSATISFIABLE_PLACEHOLDER
        }

    private fun normalizeStrangeness(strangeness: Int?): Int =
        (strangeness ?: DEFAULT_STRANGENESS).coerceIn(1, 5)

    private fun decorateVerse(provider: ISentenceProvider): ISentenceProvider =
        HtmlVerseBreaker(DexonlineLinkAdder(VerseLineCapitalizer(provider)))

    private fun decorateSentence(provider: ISentenceProvider): ISentenceProvider =
        DexonlineLinkAdder(FirstSentenceLetterCapitalizer(provider))

    companion object {
        const val DEFAULT_STRANGENESS = 2
        const val UNSATISFIABLE_PLACEHOLDER = "Nu există suficiente cuvinte pentru nivelul de stranietate ales."
    }
}
