package scrabble.phrases.repository

import io.agroal.api.AgroalDataSource
import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import scrabble.phrases.words.Adjective
import scrabble.phrases.words.Noun
import scrabble.phrases.words.NounGender
import scrabble.phrases.words.Verb
import java.util.concurrent.ThreadLocalRandom

@Startup
@ApplicationScoped
class WordRepository(private val dataSource: AgroalDataSource) {

    private var countsByTypeMaxRarity: Map<Pair<String, Int>, Int> = emptyMap()
    private var countsByTypeSyllablesMaxRarity: Map<Triple<String, Int, Int>, Int> = emptyMap()
    private var countsByTypeArticulatedSyllablesMaxRarity: Map<Triple<String, Int, Int>, Int> = emptyMap()
    private var nounRhymeGroupsMin2ByMaxRarity: Map<Int, List<String>> = emptyMap()
    private var nounRhymeGroupsMin3ByMaxRarity: Map<Int, List<String>> = emptyMap()
    private var verbRhymeGroupsMin2ByMaxRarity: Map<Int, List<String>> = emptyMap()
    private var validPrefixesByMaxRarity: Map<Int, List<String>> = emptyMap()

    @PostConstruct
    fun initCounts() {
        dataSource.connection.use { conn ->
            // Count per (type, maxRarity) cumulatively from exact rarity buckets.
            conn.prepareStatement("SELECT type, rarity_level, COUNT(*) AS cnt FROM words GROUP BY type, rarity_level").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val buckets = mutableMapOf<String, MutableMap<Int, Int>>()
                    while (rs.next()) {
                        val type = rs.getString("type")
                        val rarity = rs.getInt("rarity_level")
                        val cnt = rs.getInt("cnt")
                        buckets.computeIfAbsent(type) { mutableMapOf() }[rarity] = cnt
                    }

                    val map = mutableMapOf<Pair<String, Int>, Int>()
                    listOf("N", "A", "V").forEach { type ->
                        var running = 0
                        for (rarity in 1..MAX_RARITY) {
                            running += buckets[type]?.get(rarity) ?: 0
                            map[type to rarity] = running
                        }
                    }
                    countsByTypeMaxRarity = map
                }
            }

            countsByTypeSyllablesMaxRarity = loadCumulativeDimensionCounts(
                conn, "SELECT type, syllables, rarity_level, COUNT(*) AS cnt FROM words GROUP BY type, syllables, rarity_level", "syllables"
            )
            countsByTypeArticulatedSyllablesMaxRarity = loadCumulativeDimensionCounts(
                conn, "SELECT type, articulated_syllables, rarity_level, COUNT(*) AS cnt FROM words WHERE articulated_syllables IS NOT NULL GROUP BY type, articulated_syllables, rarity_level", "articulated_syllables"
            )

            nounRhymeGroupsMin2ByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadRhymeGroups("N", 2, rarity) }
            nounRhymeGroupsMin3ByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadRhymeGroups("N", 3, rarity) }
            verbRhymeGroupsMin2ByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadRhymeGroups("V", 2, rarity) }
            validPrefixesByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadValidPrefixes(rarity) }
        }
    }

    private fun loadCumulativeDimensionCounts(
        conn: java.sql.Connection, sql: String, dimColumn: String
    ): Map<Triple<String, Int, Int>, Int> {
        conn.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val buckets = mutableMapOf<Pair<String, Int>, MutableMap<Int, Int>>()
                while (rs.next()) {
                    val type = rs.getString("type")
                    val dim = rs.getInt(dimColumn)
                    val rarity = rs.getInt("rarity_level")
                    val cnt = rs.getInt("cnt")
                    buckets.computeIfAbsent(type to dim) { mutableMapOf() }[rarity] = cnt
                }
                val map = mutableMapOf<Triple<String, Int, Int>, Int>()
                buckets.forEach { (key, rarityCounts) ->
                    var running = 0
                    for (rarity in 1..MAX_RARITY) {
                        running += rarityCounts[rarity] ?: 0
                        map[Triple(key.first, key.second, rarity)] = running
                    }
                }
                return map
            }
        }
    }

    private fun loadRhymeGroups(type: String, minCount: Int, maxRarity: Int): List<String> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT rhyme FROM words WHERE type=? AND rarity_level<=? GROUP BY rhyme HAVING COUNT(*) >= ?"
            ).use { stmt ->
                stmt.setString(1, type)
                stmt.setInt(2, maxRarity)
                stmt.setInt(3, minCount)
                stmt.executeQuery().use { rs ->
                    val list = mutableListOf<String>()
                    while (rs.next()) list.add(rs.getString("rhyme"))
                    return list
                }
            }
        }
    }

    private fun loadValidPrefixes(maxRarity: Int): List<String> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT LEFT(word, 2) AS prefix
                FROM words
                WHERE LENGTH(word) >= 2 AND rarity_level <= ?
                GROUP BY LEFT(word, 2)
                HAVING COUNT(DISTINCT type) = 3
                """.trimIndent()
            ).use { stmt ->
                stmt.setInt(1, maxRarity)
                stmt.executeQuery().use { rs ->
                    val list = mutableListOf<String>()
                    while (rs.next()) list.add(rs.getString("prefix"))
                    return list
                }
            }
        }
    }

    private fun randomOffset(count: Int): Int =
        if (count <= 1) 0 else ThreadLocalRandom.current().nextInt(count)

    private fun <T> randomElement(list: List<T>): T? =
        if (list.isEmpty()) null else list[ThreadLocalRandom.current().nextInt(list.size)]

    private fun notInClause(count: Int): String =
        (1..count).joinToString(", ") { "?" }

    private fun clampRarity(maxRarity: Int): Int =
        maxRarity.coerceIn(1, MAX_RARITY)

    private fun rangeCount(cache: Map<Pair<String, Int>, Int>, type: String, minRarity: Int, maxRarity: Int): Int {
        val upper = cache[type to maxRarity] ?: 0
        val lower = if (minRarity <= 1) 0 else (cache[type to (minRarity - 1)] ?: 0)
        return upper - lower
    }

    private fun rangeCountTriple(cache: Map<Triple<String, Int, Int>, Int>, type: String, dim: Int, minRarity: Int, maxRarity: Int): Int {
        val upper = cache[Triple(type, dim, maxRarity)] ?: 0
        val lower = if (minRarity <= 1) 0 else (cache[Triple(type, dim, minRarity - 1)] ?: 0)
        return upper - lower
    }

    private fun rarityDesc(minRarity: Int, maxRarity: Int): String =
        if (minRarity <= 1) "<= $maxRarity" else "between $minRarity and $maxRarity"

    fun getRandomNoun(minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Noun {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                min, max, *exclude.toTypedArray()
            ) ?: throw IllegalStateException("No nouns found in database for rarity ${rarityDesc(min, max)}")
        }
        val count = rangeCount(countsByTypeMaxRarity, "N", min, max)
        if (count == 0) throw IllegalStateException("No nouns found in database for rarity ${rarityDesc(min, max)}")
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rarity_level BETWEEN ? AND ? LIMIT 1 OFFSET ?",
            min,
            max,
            randomOffset(count)
        ) ?: throw IllegalStateException("No nouns found in database for rarity ${rarityDesc(min, max)}")
    }

    fun getRandomNounByRhyme(rhyme: String, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Noun? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rhyme, min, max, *exclude.toTypedArray()
            )
        }
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? AND rarity_level BETWEEN ? AND ? ORDER BY RANDOM() LIMIT 1",
            rhyme,
            min,
            max
        )
    }

    fun getRandomNounByArticulatedSyllables(
        articulatedSyllables: Int,
        minRarity: Int = 1,
        maxRarity: Int = DEFAULT_MAX_RARITY,
        exclude: Set<String> = emptySet()
    ): Noun? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND articulated_syllables=? AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                articulatedSyllables, min, max, *exclude.toTypedArray()
            )
        }

        val count = rangeCountTriple(countsByTypeArticulatedSyllablesMaxRarity, "N", articulatedSyllables, min, max)
        if (count == 0) return null
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND articulated_syllables=? AND rarity_level BETWEEN ? AND ? LIMIT 1 OFFSET ?",
            articulatedSyllables,
            min,
            max,
            randomOffset(count)
        )
    }

    fun getRandomAdjective(minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Adjective {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryAdjective(
                "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                min, max, *exclude.toTypedArray()
            ) ?: throw IllegalStateException("No adjectives found in database for rarity ${rarityDesc(min, max)}")
        }
        val count = rangeCount(countsByTypeMaxRarity, "A", min, max)
        if (count == 0) throw IllegalStateException("No adjectives found in database for rarity ${rarityDesc(min, max)}")
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND rarity_level BETWEEN ? AND ? LIMIT 1 OFFSET ?",
            min,
            max,
            randomOffset(count)
        ) ?: throw IllegalStateException("No adjectives found in database for rarity ${rarityDesc(min, max)}")
    }

    fun getRandomAdjectiveBySyllables(syllables: Int, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY): Adjective? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        val count = rangeCountTriple(countsByTypeSyllablesMaxRarity, "A", syllables, min, max)
        if (count == 0) return null
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND syllables=? AND rarity_level BETWEEN ? AND ? LIMIT 1 OFFSET ?",
            syllables,
            min,
            max,
            randomOffset(count)
        )
    }

    fun getRandomVerb(minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Verb {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryVerb(
                "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                min, max, *exclude.toTypedArray()
            ) ?: throw IllegalStateException("No verbs found in database for rarity ${rarityDesc(min, max)}")
        }
        val count = rangeCount(countsByTypeMaxRarity, "V", min, max)
        if (count == 0) throw IllegalStateException("No verbs found in database for rarity ${rarityDesc(min, max)}")
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rarity_level BETWEEN ? AND ? LIMIT 1 OFFSET ?",
            min,
            max,
            randomOffset(count)
        ) ?: throw IllegalStateException("No verbs found in database for rarity ${rarityDesc(min, max)}")
    }

    fun getRandomVerbByRhyme(rhyme: String, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Verb? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryVerb(
                "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rhyme=? AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rhyme, min, max, *exclude.toTypedArray()
            )
        }
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rhyme=? AND rarity_level BETWEEN ? AND ? ORDER BY RANDOM() LIMIT 1",
            rhyme,
            min,
            max
        )
    }

    fun getRandomVerbBySyllables(syllables: Int, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY): Verb? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        val count = rangeCountTriple(countsByTypeSyllablesMaxRarity, "V", syllables, min, max)
        if (count == 0) return null
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND syllables=? AND rarity_level BETWEEN ? AND ? LIMIT 1 OFFSET ?",
            syllables,
            min,
            max,
            randomOffset(count)
        )
    }

    fun findTwoRhymeGroups(type: String, minCount: Int, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY): Pair<String, String>? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (min <= 1) {
            val cached = when {
                type == "N" && minCount <= 2 -> nounRhymeGroupsMin2ByMaxRarity[max]
                type == "N" && minCount <= 3 -> nounRhymeGroupsMin3ByMaxRarity[max]
                type == "V" && minCount <= 2 -> verbRhymeGroupsMin2ByMaxRarity[max]
                else -> null
            }
            if (cached != null && cached.size >= 2) {
                val shuffled = cached.shuffled(ThreadLocalRandom.current())
                return Pair(shuffled[0], shuffled[1])
            }
        }
        return findTwoRhymeGroupsFromDb(type, minCount, min, max)
    }

    fun getRandomPrefixWithAllTypes(minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY): String? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (min <= 1) {
            val cached = validPrefixesByMaxRarity[max]
            if (!cached.isNullOrEmpty()) return randomElement(cached)
        }
        return getRandomPrefixWithAllTypesFromDb(min, max)
    }

    fun getRandomNounByPrefix(prefix: String, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Noun? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND word LIKE ? AND rarity_level BETWEEN ? AND ? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                "$prefix%", min, max, *exclude.toTypedArray()
            )
        }
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND word LIKE ? AND rarity_level BETWEEN ? AND ? ORDER BY RANDOM() LIMIT 1",
            "$prefix%",
            min,
            max
        )
    }

    fun getRandomAdjectiveByPrefix(prefix: String, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY): Adjective? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND word LIKE ? AND rarity_level BETWEEN ? AND ? ORDER BY RANDOM() LIMIT 1",
            "$prefix%",
            min,
            max
        )
    }

    fun getRandomVerbByPrefix(prefix: String, minRarity: Int = 1, maxRarity: Int = DEFAULT_MAX_RARITY): Verb? {
        val min = clampRarity(minRarity)
        val max = clampRarity(maxRarity)
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND word LIKE ? AND rarity_level BETWEEN ? AND ? ORDER BY RANDOM() LIMIT 1",
            "$prefix%",
            min,
            max
        )
    }

    private fun findTwoRhymeGroupsFromDb(type: String, minCount: Int, minRarity: Int, maxRarity: Int): Pair<String, String>? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT rhyme FROM words WHERE type=? AND rarity_level BETWEEN ? AND ? GROUP BY rhyme HAVING COUNT(*)>=? ORDER BY RANDOM() LIMIT 2"
            ).use { stmt ->
                stmt.setString(1, type)
                stmt.setInt(2, minRarity)
                stmt.setInt(3, maxRarity)
                stmt.setInt(4, minCount)
                stmt.executeQuery().use { rs ->
                    val rhyme1 = if (rs.next()) rs.getString("rhyme") else return null
                    val rhyme2 = if (rs.next()) rs.getString("rhyme") else return null
                    return Pair(rhyme1, rhyme2)
                }
            }
        }
    }

    private fun getRandomPrefixWithAllTypesFromDb(minRarity: Int, maxRarity: Int): String? {
        dataSource.connection.use { conn ->
            // Step 1: pick a few random nouns to get candidate two-letter prefixes
            val prefixes = mutableListOf<String>()
            conn.prepareStatement(
                "SELECT word FROM words WHERE type='N' AND LENGTH(word) >= 2 AND rarity_level BETWEEN ? AND ? ORDER BY RANDOM() LIMIT 5"
            ).use { stmt ->
                stmt.setInt(1, minRarity)
                stmt.setInt(2, maxRarity)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val w = rs.getString("word")
                        val p = w.substring(0, 2)
                        if (p !in prefixes) prefixes.add(p)
                    }
                }
            }
            if (prefixes.isEmpty()) return null

            // Step 2: verify which candidate prefixes have all 3 word types
            val placeholders = prefixes.joinToString(", ") { "?" }
            conn.prepareStatement(
                """
                SELECT LEFT(word, 2) AS prefix
                FROM words
                WHERE LEFT(word, 2) IN ($placeholders) AND rarity_level BETWEEN ? AND ?
                GROUP BY LEFT(word, 2)
                HAVING COUNT(DISTINCT type) = 3
                ORDER BY RANDOM() LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                var idx = 1
                for (p in prefixes) stmt.setString(idx++, p)
                stmt.setInt(idx++, minRarity)
                stmt.setInt(idx, maxRarity)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("prefix") else null
                }
            }
        }
    }

    private fun <T> queryRow(sql: String, mapper: (java.sql.ResultSet) -> T, vararg params: Any): T? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, param -> stmt.setObject(i + 1, param) }
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapper(rs) else null
                }
            }
        }
    }

    private fun queryNoun(sql: String, vararg params: Any): Noun? = queryRow(sql, ::mapNoun, *params)
    private fun queryAdjective(sql: String, vararg params: Any): Adjective? = queryRow(sql, ::mapAdjective, *params)
    private fun queryVerb(sql: String, vararg params: Any): Verb? = queryRow(sql, ::mapVerb, *params)

    private fun mapNoun(rs: java.sql.ResultSet): Noun = Noun(
        word = rs.getString("word"),
        gender = NounGender.valueOf(rs.getString("gender")),
        syllables = rs.getInt("syllables"),
        rhyme = rs.getString("rhyme"),
        articulated = rs.getString("articulated") ?: ""
    )

    private fun mapAdjective(rs: java.sql.ResultSet): Adjective = Adjective(
        word = rs.getString("word"),
        syllables = rs.getInt("syllables"),
        rhyme = rs.getString("rhyme"),
        feminine = rs.getString("feminine") ?: ""
    )

    private fun mapVerb(rs: java.sql.ResultSet): Verb = Verb(
        word = rs.getString("word"),
        syllables = rs.getInt("syllables"),
        rhyme = rs.getString("rhyme")
    )

    companion object {
        const val DEFAULT_MAX_RARITY = 4
        const val MAX_RARITY = 5
    }
}
