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

    private var countsByType: Map<String, Int> = emptyMap()
    private var countsByTypeSyllables: Map<Pair<String, Int>, Int> = emptyMap()
    private var countsByTypeArticulatedSyllables: Map<Pair<String, Int>, Int> = emptyMap()
    private var nounRhymeGroupsMin2: List<String> = emptyList()
    private var nounRhymeGroupsMin4: List<String> = emptyList()
    private var validPrefixes: List<String> = emptyList()

    @PostConstruct
    fun initCounts() {
        dataSource.connection.use { conn ->
            // Count per type
            conn.prepareStatement("SELECT type, COUNT(*) AS cnt FROM words GROUP BY type").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val map = mutableMapOf<String, Int>()
                    while (rs.next()) {
                        map[rs.getString("type")] = rs.getInt("cnt")
                    }
                    countsByType = map
                }
            }

            // Count per (type, syllables)
            conn.prepareStatement("SELECT type, syllables, COUNT(*) AS cnt FROM words GROUP BY type, syllables").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val map = mutableMapOf<Pair<String, Int>, Int>()
                    while (rs.next()) {
                        map[Pair(rs.getString("type"), rs.getInt("syllables"))] = rs.getInt("cnt")
                    }
                    countsByTypeSyllables = map
                }
            }

            // Count per (type, articulated_syllables)
            conn.prepareStatement("SELECT type, articulated_syllables, COUNT(*) AS cnt FROM words WHERE articulated_syllables IS NOT NULL GROUP BY type, articulated_syllables").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val map = mutableMapOf<Pair<String, Int>, Int>()
                    while (rs.next()) {
                        map[Pair(rs.getString("type"), rs.getInt("articulated_syllables"))] = rs.getInt("cnt")
                    }
                    countsByTypeArticulatedSyllables = map
                }
            }

            // Noun rhyme groups with >= 2 nouns (for MirrorProvider)
            conn.prepareStatement("SELECT rhyme FROM words WHERE type='N' GROUP BY rhyme HAVING COUNT(*) >= 2").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val list = mutableListOf<String>()
                    while (rs.next()) list.add(rs.getString("rhyme"))
                    nounRhymeGroupsMin2 = list
                }
            }

            // Noun rhyme groups with >= 4 nouns (for CoupletProvider)
            conn.prepareStatement("SELECT rhyme FROM words WHERE type='N' GROUP BY rhyme HAVING COUNT(*) >= 4").use { stmt ->
                stmt.executeQuery().use { rs ->
                    val list = mutableListOf<String>()
                    while (rs.next()) list.add(rs.getString("rhyme"))
                    nounRhymeGroupsMin4 = list
                }
            }

            // Valid 2-letter prefixes that have all 3 types (for TautogramProvider)
            conn.prepareStatement("""
                SELECT LEFT(word, 2) AS prefix
                FROM words
                WHERE LENGTH(word) >= 2
                GROUP BY LEFT(word, 2)
                HAVING COUNT(DISTINCT type) = 3
            """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val list = mutableListOf<String>()
                    while (rs.next()) list.add(rs.getString("prefix"))
                    validPrefixes = list
                }
            }
        }
    }

    private fun randomOffset(count: Int): Int =
        if (count <= 1) 0 else ThreadLocalRandom.current().nextInt(count)

    private fun <T> randomElement(list: List<T>): T? =
        if (list.isEmpty()) null else list[ThreadLocalRandom.current().nextInt(list.size)]

    fun getRandomNoun(): Noun {
        val count = countsByType["N"] ?: 0
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' LIMIT 1 OFFSET ?",
            randomOffset(count)
        ) ?: throw IllegalStateException("No nouns found in database")
    }

    fun getRandomNounByRhyme(rhyme: String): Noun? =
        queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? ORDER BY RANDOM() LIMIT 1",
            rhyme
        )

    fun getRandomNounByArticulatedSyllables(articulatedSyllables: Int): Noun? {
        val count = countsByTypeArticulatedSyllables[Pair("N", articulatedSyllables)] ?: 0
        if (count == 0) return null
        return queryNoun(
            "SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND articulated_syllables=? LIMIT 1 OFFSET ?",
            articulatedSyllables,
            randomOffset(count)
        )
    }

    fun getRandomAdjective(): Adjective {
        val count = countsByType["A"] ?: 0
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' LIMIT 1 OFFSET ?",
            randomOffset(count)
        ) ?: throw IllegalStateException("No adjectives found in database")
    }

    fun getRandomAdjectiveBySyllables(syllables: Int): Adjective? {
        val count = countsByTypeSyllables[Pair("A", syllables)] ?: 0
        if (count == 0) return null
        return queryAdjective(
            "SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND syllables=? LIMIT 1 OFFSET ?",
            syllables,
            randomOffset(count)
        )
    }

    fun getRandomVerb(): Verb {
        val count = countsByType["V"] ?: 0
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' LIMIT 1 OFFSET ?",
            randomOffset(count)
        ) ?: throw IllegalStateException("No verbs found in database")
    }

    fun getRandomVerbBySyllables(syllables: Int): Verb? {
        val count = countsByTypeSyllables[Pair("V", syllables)] ?: 0
        if (count == 0) return null
        return queryVerb(
            "SELECT word, syllables, rhyme FROM words WHERE type='V' AND syllables=? LIMIT 1 OFFSET ?",
            syllables,
            randomOffset(count)
        )
    }

    fun findRhymeGroup(type: String, minCount: Int): String? {
        val cached = when {
            type == "N" && minCount <= 2 -> nounRhymeGroupsMin2
            type == "N" && minCount <= 4 -> nounRhymeGroupsMin4
            else -> null
        }
        if (cached != null) return randomElement(cached)
        return findRhymeGroupFromDb(type, minCount)
    }

    fun findTwoRhymeGroups(type: String, minCount: Int): Pair<String, String>? {
        val cached = when {
            type == "N" && minCount <= 2 -> nounRhymeGroupsMin2
            type == "N" && minCount <= 4 -> nounRhymeGroupsMin4
            else -> null
        }
        if (cached != null && cached.size >= 2) {
            val shuffled = cached.shuffled(ThreadLocalRandom.current())
            return Pair(shuffled[0], shuffled[1])
        }
        return findTwoRhymeGroupsFromDb(type, minCount)
    }

    fun getNounsByRhyme(rhyme: String, limit: Int): List<Noun> {
        val nouns = mutableListOf<Noun>()
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? ORDER BY RANDOM() LIMIT ?").use { stmt ->
                stmt.setString(1, rhyme)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) nouns.add(mapNoun(rs))
                }
            }
        }
        return nouns
    }

    fun getRandomPrefixWithAllTypes(): String? {
        if (validPrefixes.isNotEmpty()) return randomElement(validPrefixes)
        return getRandomPrefixWithAllTypesFromDb()
    }

    fun getRandomNounByPrefix(prefix: String): Noun? =
        queryNoun("SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND word LIKE ? ORDER BY RANDOM() LIMIT 1", "$prefix%")

    fun getRandomAdjectiveByPrefix(prefix: String): Adjective? =
        queryAdjective("SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND word LIKE ? ORDER BY RANDOM() LIMIT 1", "$prefix%")

    fun getRandomVerbByPrefix(prefix: String): Verb? =
        queryVerb("SELECT word, syllables, rhyme FROM words WHERE type='V' AND word LIKE ? ORDER BY RANDOM() LIMIT 1", "$prefix%")

    private fun findRhymeGroupFromDb(type: String, minCount: Int): String? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT rhyme FROM words WHERE type=? GROUP BY rhyme HAVING COUNT(*)>=? ORDER BY RANDOM() LIMIT 1").use { stmt ->
                stmt.setString(1, type)
                stmt.setInt(2, minCount)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getString("rhyme") else null
                }
            }
        }
    }

    private fun findTwoRhymeGroupsFromDb(type: String, minCount: Int): Pair<String, String>? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT rhyme FROM words WHERE type=? GROUP BY rhyme HAVING COUNT(*)>=? ORDER BY RANDOM() LIMIT 2").use { stmt ->
                stmt.setString(1, type)
                stmt.setInt(2, minCount)
                stmt.executeQuery().use { rs ->
                    val rhyme1 = if (rs.next()) rs.getString("rhyme") else return null
                    val rhyme2 = if (rs.next()) rs.getString("rhyme") else return null
                    return Pair(rhyme1, rhyme2)
                }
            }
        }
    }

    private fun getRandomPrefixWithAllTypesFromDb(): String? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT prefix FROM (
                    SELECT LEFT(word, 2) AS prefix
                    FROM words
                    WHERE LENGTH(word) >= 2
                    GROUP BY LEFT(word, 2)
                    HAVING COUNT(DISTINCT type) = 3
                ) valid_prefixes
                ORDER BY RANDOM() LIMIT 1
            """.trimIndent()).use { stmt ->
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
}
