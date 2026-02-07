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

            // Count per (type, syllables, maxRarity)
            conn.prepareStatement("SELECT type, syllables, rarity_level, COUNT(*) AS cnt FROM words GROUP BY type, syllables, rarity_level").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val buckets = mutableMapOf<Pair<String, Int>, MutableMap<Int, Int>>()
                    while (rs.next()) {
                        val type = rs.getString("type")
                        val syllables = rs.getInt("syllables")
                        val rarity = rs.getInt("rarity_level")
                        val cnt = rs.getInt("cnt")
                        buckets.computeIfAbsent(type to syllables) { mutableMapOf() }[rarity] = cnt
                    }

                    val map = mutableMapOf<Triple<String, Int, Int>, Int>()
                    buckets.forEach { (key, rarityCounts) ->
                        var running = 0
                        for (rarity in 1..MAX_RARITY) {
                            running += rarityCounts[rarity] ?: 0
                            map[Triple(key.first, key.second, rarity)] = running
                        }
                    }
                    countsByTypeSyllablesMaxRarity = map
                }
            }

            // Count per (type, articulated_syllables, maxRarity)
            conn.prepareStatement("SELECT type, articulated_syllables, rarity_level, COUNT(*) AS cnt FROM words WHERE articulated_syllables IS NOT NULL GROUP BY type, articulated_syllables, rarity_level").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val buckets = mutableMapOf<Pair<String, Int>, MutableMap<Int, Int>>()
                    while (rs.next()) {
                        val type = rs.getString("type")
                        val articulatedSyllables = rs.getInt("articulated_syllables")
                        val rarity = rs.getInt("rarity_level")
                        val cnt = rs.getInt("cnt")
                        buckets.computeIfAbsent(type to articulatedSyllables) { mutableMapOf() }[rarity] = cnt
                    }

                    val map = mutableMapOf<Triple<String, Int, Int>, Int>()
                    buckets.forEach { (key, rarityCounts) ->
                        var running = 0
                        for (rarity in 1..MAX_RARITY) {
                            running += rarityCounts[rarity] ?: 0
                            map[Triple(key.first, key.second, rarity)] = running
                        }
                    }
                    countsByTypeArticulatedSyllablesMaxRarity = map
                }
            }

            nounRhymeGroupsMin2ByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadRhymeGroups("N", 2, rarity) }
            nounRhymeGroupsMin3ByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadRhymeGroups("N", 3, rarity) }
            verbRhymeGroupsMin2ByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadRhymeGroups("V", 2, rarity) }
            validPrefixesByMaxRarity = (1..MAX_RARITY).associateWith { rarity -> loadValidPrefixes(rarity) }
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

    fun getRandomNoun(maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Noun {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rarity, *exclude.toTypedArray()
            ) ?: throw IllegalStateException("No nouns found in database for rarity <= $rarity")
        }
        val count = countsByTypeMaxRarity["N" to rarity] ?: 0
        if (count == 0) throw IllegalStateException("No nouns found in database for rarity <= $rarity")
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rarity_level<=? LIMIT 1 OFFSET ?",
            rarity,
            randomOffset(count)
        ) ?: throw IllegalStateException("No nouns found in database for rarity <= $rarity")
    }

    fun getRandomNounByRhyme(rhyme: String, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Noun? {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rhyme, rarity, *exclude.toTypedArray()
            )
        }
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? AND rarity_level<=? ORDER BY RANDOM() LIMIT 1",
            rhyme,
            rarity
        )
    }

    fun getRandomNounByArticulatedSyllables(
        articulatedSyllables: Int,
        maxRarity: Int = DEFAULT_MAX_RARITY,
        exclude: Set<String> = emptySet()
    ): Noun? {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND articulated_syllables=? AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                articulatedSyllables, rarity, *exclude.toTypedArray()
            )
        }

        val count = countsByTypeArticulatedSyllablesMaxRarity[Triple("N", articulatedSyllables, rarity)] ?: 0
        if (count == 0) return null
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND articulated_syllables=? AND rarity_level<=? LIMIT 1 OFFSET ?",
            articulatedSyllables,
            rarity,
            randomOffset(count)
        )
    }

    fun getRandomAdjective(maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Adjective {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryAdjective(
                "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rarity, *exclude.toTypedArray()
            ) ?: throw IllegalStateException("No adjectives found in database for rarity <= $rarity")
        }
        val count = countsByTypeMaxRarity["A" to rarity] ?: 0
        if (count == 0) throw IllegalStateException("No adjectives found in database for rarity <= $rarity")
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND rarity_level<=? LIMIT 1 OFFSET ?",
            rarity,
            randomOffset(count)
        ) ?: throw IllegalStateException("No adjectives found in database for rarity <= $rarity")
    }

    fun getRandomAdjectiveBySyllables(syllables: Int, maxRarity: Int = DEFAULT_MAX_RARITY): Adjective? {
        val rarity = clampRarity(maxRarity)
        val count = countsByTypeSyllablesMaxRarity[Triple("A", syllables, rarity)] ?: 0
        if (count == 0) return null
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND syllables=? AND rarity_level<=? LIMIT 1 OFFSET ?",
            syllables,
            rarity,
            randomOffset(count)
        )
    }

    fun getRandomVerb(maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Verb {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryVerb(
                "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rarity, *exclude.toTypedArray()
            ) ?: throw IllegalStateException("No verbs found in database for rarity <= $rarity")
        }
        val count = countsByTypeMaxRarity["V" to rarity] ?: 0
        if (count == 0) throw IllegalStateException("No verbs found in database for rarity <= $rarity")
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rarity_level<=? LIMIT 1 OFFSET ?",
            rarity,
            randomOffset(count)
        ) ?: throw IllegalStateException("No verbs found in database for rarity <= $rarity")
    }

    fun getRandomVerbByRhyme(rhyme: String, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Verb? {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryVerb(
                "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rhyme=? AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                rhyme, rarity, *exclude.toTypedArray()
            )
        }
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND rhyme=? AND rarity_level<=? ORDER BY RANDOM() LIMIT 1",
            rhyme,
            rarity
        )
    }

    fun getRandomVerbBySyllables(syllables: Int, maxRarity: Int = DEFAULT_MAX_RARITY): Verb? {
        val rarity = clampRarity(maxRarity)
        val count = countsByTypeSyllablesMaxRarity[Triple("V", syllables, rarity)] ?: 0
        if (count == 0) return null
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND syllables=? AND rarity_level<=? LIMIT 1 OFFSET ?",
            syllables,
            rarity,
            randomOffset(count)
        )
    }

    fun findTwoRhymeGroups(type: String, minCount: Int, maxRarity: Int = DEFAULT_MAX_RARITY): Pair<String, String>? {
        val rarity = clampRarity(maxRarity)
        val cached = when {
            type == "N" && minCount <= 2 -> nounRhymeGroupsMin2ByMaxRarity[rarity]
            type == "N" && minCount <= 3 -> nounRhymeGroupsMin3ByMaxRarity[rarity]
            type == "V" && minCount <= 2 -> verbRhymeGroupsMin2ByMaxRarity[rarity]
            else -> null
        }
        if (cached != null && cached.size >= 2) {
            val shuffled = cached.shuffled(ThreadLocalRandom.current())
            return Pair(shuffled[0], shuffled[1])
        }
        return findTwoRhymeGroupsFromDb(type, minCount, rarity)
    }

    fun getRandomPrefixWithAllTypes(maxRarity: Int = DEFAULT_MAX_RARITY): String? {
        val rarity = clampRarity(maxRarity)
        val cached = validPrefixesByMaxRarity[rarity]
        if (!cached.isNullOrEmpty()) return randomElement(cached)
        return getRandomPrefixWithAllTypesFromDb(rarity)
    }

    fun getRandomNounByPrefix(prefix: String, maxRarity: Int = DEFAULT_MAX_RARITY, exclude: Set<String> = emptySet()): Noun? {
        val rarity = clampRarity(maxRarity)
        if (exclude.isNotEmpty()) {
            val notIn = notInClause(exclude.size)
            return queryNoun(
                "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND word LIKE ? AND rarity_level<=? AND word NOT IN ($notIn) ORDER BY RANDOM() LIMIT 1",
                "$prefix%", rarity, *exclude.toTypedArray()
            )
        }
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND word LIKE ? AND rarity_level<=? ORDER BY RANDOM() LIMIT 1",
            "$prefix%",
            rarity
        )
    }

    fun getRandomAdjectiveByPrefix(prefix: String, maxRarity: Int = DEFAULT_MAX_RARITY): Adjective? {
        val rarity = clampRarity(maxRarity)
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND word LIKE ? AND rarity_level<=? ORDER BY RANDOM() LIMIT 1",
            "$prefix%",
            rarity
        )
    }

    fun getRandomVerbByPrefix(prefix: String, maxRarity: Int = DEFAULT_MAX_RARITY): Verb? {
        val rarity = clampRarity(maxRarity)
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND word LIKE ? AND rarity_level<=? ORDER BY RANDOM() LIMIT 1",
            "$prefix%",
            rarity
        )
    }

    private fun findTwoRhymeGroupsFromDb(type: String, minCount: Int, maxRarity: Int): Pair<String, String>? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT rhyme FROM words WHERE type=? AND rarity_level<=? GROUP BY rhyme HAVING COUNT(*)>=? ORDER BY RANDOM() LIMIT 2"
            ).use { stmt ->
                stmt.setString(1, type)
                stmt.setInt(2, maxRarity)
                stmt.setInt(3, minCount)
                stmt.executeQuery().use { rs ->
                    val rhyme1 = if (rs.next()) rs.getString("rhyme") else return null
                    val rhyme2 = if (rs.next()) rs.getString("rhyme") else return null
                    return Pair(rhyme1, rhyme2)
                }
            }
        }
    }

    private fun getRandomPrefixWithAllTypesFromDb(maxRarity: Int): String? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT prefix FROM (
                    SELECT LEFT(word, 2) AS prefix
                    FROM words
                    WHERE LENGTH(word) >= 2 AND rarity_level <= ?
                    GROUP BY LEFT(word, 2)
                    HAVING COUNT(DISTINCT type) = 3
                ) valid_prefixes
                ORDER BY RANDOM() LIMIT 1
                """.trimIndent()
            ).use { stmt ->
                stmt.setInt(1, maxRarity)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("prefix") else null
                }
            }
        }
    }

    private fun queryNoun(sql: String, vararg params: Any): Noun? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, param -> stmt.setObject(i + 1, param) }
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapNoun(rs) else null
                }
            }
        }
    }

    private fun queryAdjective(sql: String, vararg params: Any): Adjective? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, param -> stmt.setObject(i + 1, param) }
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapAdjective(rs) else null
                }
            }
        }
    }

    private fun queryVerb(sql: String, vararg params: Any): Verb? {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, param -> stmt.setObject(i + 1, param) }
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapVerb(rs) else null
                }
            }
        }
    }

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
