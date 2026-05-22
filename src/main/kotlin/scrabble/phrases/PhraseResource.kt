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
import scrabble.phrases.providers.DistihProvider
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

    /**
     * Calculates the effective rarity range [min, max] for a request.
     *
     * @param rarity The requested rarity level (optional).
     * @param minRarity The minimum allowed rarity level (optional, defaults to 1).
     * @return A Pair representing the clamped and normalized range [min, max].
     */
    private fun rarityRange(rarity: Int?, minRarity: Int?): Pair<Int, Int> {
        val a = normalizeRarity(rarity)
        val b = normalizeRarity(minRarity, default = 1)
        // Ensure the range is always [min, max] regardless of parameter order.
        return Pair(minOf(a, b), max(a, b))
    }

    /**
     * Normalizes a rarity level to be within the valid range [1, 5].
     *
     * @param rarity The input rarity value to normalize.
     * @param default The value to use if the input is null (defaults to 2).
     * @return A clamped integer between 1 and 5.
     */
    private fun normalizeRarity(rarity: Int?, default: Int = DEFAULT_RARITY): Int =
        (rarity ?: default).coerceIn(1, 5)
        decorate: (ISentenceProvider) -> ISentenceProvider,
        providerFactory: (Int, Int) -> ISentenceProvider
    ): SentenceResponse {
        val (min, max) = rarityRange(rarity, minRarity)
        return SentenceResponse(safeGenerate { decorate(providerFactory(min, max)).getSentence() })
    }

    @GET
    @Path("/haiku")
    fun getHaiku(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): SentenceResponse =
        generate(rarity, minRarity, ::decorateVerse) { min, max -> HaikuProvider(wordRepository, minRarity = min, maxRarity = max) }

    @GET
    @Path("/distih")
    fun getDistih(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): SentenceResponse =
        generate(rarity, minRarity, ::decorateVerse) { min, max -> DistihProvider(wordRepository, minRarity = min, maxRarity = max) }

    @GET
    @Path("/comparison")
    fun getComparison(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): SentenceResponse =
        generate(rarity, minRarity, ::decorateSentence) { min, max -> ComparisonProvider(wordRepository, minRarity = min, maxRarity = max) }

    @GET
    @Path("/definition")
    fun getDefinition(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): SentenceResponse =
        generate(rarity, minRarity, ::decorateSentence) { min, max -> DefinitionProvider(wordRepository, minRarity = min, maxRarity = max) }

    @GET
    @Path("/tautogram")
    fun getTautogram(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): SentenceResponse =
        generate(rarity, minRarity, ::decorateSentence) { min, max -> TautogramProvider(wordRepository, minRarity = min, maxRarity = max) }

    @GET
    @Path("/all")
    fun getAll(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): AllSentencesResponse =
        AllSentencesResponse(
            haiku = getHaiku(rarity, minRarity).sentence,
            distih = getDistih(rarity, minRarity).sentence,
            comparison = getComparison(rarity, minRarity).sentence,
            definition = getDefinition(rarity, minRarity).sentence,
            tautogram = getTautogram(rarity, minRarity).sentence,
            mirror = getMirror(rarity, minRarity).sentence
        )

    @GET
    @Path("/mirror")
    fun getMirror(@QueryParam("rarity") rarity: Int?, @QueryParam("minRarity") minRarity: Int?): SentenceResponse =
        generate(rarity, minRarity, ::decorateVerse) { min, max -> MirrorProvider(wordRepository, minRarity = min, maxRarity = max) }

    private fun safeGenerate(generator: () -> String): String =
        try {
            generator()
        } catch (_: IllegalStateException) {
            UNSATISFIABLE_PLACEHOLDER
        }

    private fun normalizeRarity(rarity: Int?, default: Int = DEFAULT_RARITY): Int =
        (rarity ?: default).coerceIn(1, 5)

    private fun decorateVerse(provider: ISentenceProvider): ISentenceProvider =
        HtmlVerseBreaker(DexonlineLinkAdder(VerseLineCapitalizer(provider)))

    private fun decorateSentence(provider: ISentenceProvider): ISentenceProvider =
        DexonlineLinkAdder(FirstSentenceLetterCapitalizer(provider))

    companion object {
        const val DEFAULT_RARITY = 2
        const val UNSATISFIABLE_PLACEHOLDER = "Nu există suficiente cuvinte pentru nivelul de raritate ales."
    }
}
