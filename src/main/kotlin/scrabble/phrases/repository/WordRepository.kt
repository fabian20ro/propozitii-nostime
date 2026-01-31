package scrabble.phrases.repository

import io.agroal.api.AgroalDataSource
import jakarta.enterprise.context.ApplicationScoped
import scrabble.phrases.words.*

@ApplicationScoped
class WordRepository(private val dataSource: AgroalDataSource) {

    fun getRandomNoun(): Noun =
        queryNoun("SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' ORDER BY RANDOM() LIMIT 1")
            ?: throw IllegalStateException("No nouns found in database")

    fun getRandomNounByRhyme(rhyme: String): Noun? =
        queryNoun("SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND rhyme=? ORDER BY RANDOM() LIMIT 1", rhyme)

    fun getRandomNounByFirstLetter(letter: Char): Noun? =
        queryNoun("SELECT word, gender, syllables, rhyme, articulated FROM words WHERE type='N' AND first_letter=? ORDER BY RANDOM() LIMIT 1", letter.lowercaseChar().toString())

    fun getRandomNounByArticulatedSyllables(articulatedSyllables: Int): Noun? {
        // Filter by articulated syllable count in code since it's computed from the articulated form
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT word, gender, syllables, rhyme, articulated FROM words
                WHERE type='N' AND articulated IS NOT NULL
                ORDER BY RANDOM() LIMIT 200
            """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val noun = mapNoun(rs)
                        if (WordUtils.computeSyllableNumber(noun.articulated) == articulatedSyllables) {
                            return noun
                        }
                    }
                }
            }
        }
        return null
    }

    fun getRandomNounByRhymeAndArticulatedSyllables(rhyme: String, articulatedSyllables: Int): Noun? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT word, gender, syllables, rhyme, articulated FROM words
                WHERE type='N' AND rhyme=? AND articulated IS NOT NULL
                ORDER BY RANDOM() LIMIT 100
            """.trimIndent()).use { stmt ->
                stmt.setString(1, rhyme)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val noun = mapNoun(rs)
                        if (WordUtils.computeSyllableNumber(noun.articulated) == articulatedSyllables) {
                            return noun
                        }
                    }
                }
            }
        }
        return null
    }

    fun getRandomAdjective(): Adjective =
        queryAdjective("SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' ORDER BY RANDOM() LIMIT 1")
            ?: throw IllegalStateException("No adjectives found in database")

    fun getRandomAdjectiveBySyllables(syllables: Int): Adjective? =
        queryAdjective("SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND syllables=? ORDER BY RANDOM() LIMIT 1", syllables)

    fun getRandomAdjectiveByFirstLetter(letter: Char): Adjective? =
        queryAdjective("SELECT word, syllables, rhyme, feminine FROM words WHERE type='A' AND first_letter=? ORDER BY RANDOM() LIMIT 1", letter.lowercaseChar().toString())

    fun getRandomVerb(): Verb =
        queryVerb("SELECT word, syllables, rhyme FROM words WHERE type='V' ORDER BY RANDOM() LIMIT 1")
            ?: throw IllegalStateException("No verbs found in database")

    fun getRandomVerbBySyllables(syllables: Int): Verb? =
        queryVerb("SELECT word, syllables, rhyme FROM words WHERE type='V' AND syllables=? ORDER BY RANDOM() LIMIT 1", syllables)

    fun getRandomVerbByFirstLetter(letter: Char): Verb? =
        queryVerb("SELECT word, syllables, rhyme FROM words WHERE type='V' AND first_letter=? ORDER BY RANDOM() LIMIT 1", letter.lowercaseChar().toString())

    fun findRhymeGroup(type: String, minCount: Int): String? {
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

    fun findTwoRhymeGroups(type: String, minCount: Int): Pair<String, String>? {
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

    fun hasWordsForLetter(letter: Char): Boolean {
        val letterStr = letter.lowercaseChar().toString()
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM words WHERE type='N' AND first_letter=?)
                   AND EXISTS(SELECT 1 FROM words WHERE type='A' AND first_letter=?)
                   AND EXISTS(SELECT 1 FROM words WHERE type='V' AND first_letter=?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, letterStr)
                stmt.setString(2, letterStr)
                stmt.setString(3, letterStr)
                stmt.executeQuery().use { rs ->
                    return rs.next() && rs.getBoolean(1)
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
