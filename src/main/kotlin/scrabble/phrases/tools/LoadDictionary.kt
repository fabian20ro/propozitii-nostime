package scrabble.phrases.tools

import scrabble.phrases.words.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    val dbUrl = System.getenv("SUPABASE_DB_URL")
        ?: "jdbc:postgresql://localhost:5432/postgres"
    val dbUser = System.getenv("SUPABASE_DB_USER") ?: "postgres"
    val dbPassword = System.getenv("SUPABASE_DB_PASSWORD")
        ?: throw IllegalStateException("SUPABASE_DB_PASSWORD environment variable is required")

    val wordsFile = File("src/main/resources/words.txt")
    if (!wordsFile.exists()) {
        println("words.txt not found. Run './gradlew downloadDictionary' first.")
        return
    }

    println("Connecting to database...")
    val conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    conn.autoCommit = false

    try {
        println("Truncating words table...")
        conn.createStatement().use { it.execute("TRUNCATE TABLE words") }
        loadWords(conn, wordsFile)
        conn.commit()
        println("Dictionary loaded successfully!")
    } catch (e: Exception) {
        conn.rollback()
        throw e
    } finally {
        conn.close()
    }
}

private fun loadWords(conn: Connection, wordsFile: File) {
    println("Loading words from ${wordsFile.name}...")

    val insertSql = """
        INSERT INTO words (word, type, gender, syllables, rhyme, first_letter, articulated, feminine, articulated_syllables, rarity_level, feminine_syllables)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    conn.prepareStatement(insertSql).use { stmt ->
        var count = 0
        var skipped = 0

        BufferedReader(InputStreamReader(wordsFile.inputStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.forEachLine { line ->
                val pieces = line.trim().split(Regex("\\s+"))
                if (pieces.size >= 2) {
                    var word = WordUtils.fixWordCharacters(pieces[0])
                    var type = pieces[1]

                    // Handle numeric type format
                    try {
                        type.toInt()
                        val breakIndex = if (word[word.length - 2] <= 'Z') 2 else 1
                        type = word.substring(word.length - breakIndex)
                        word = word.substring(0, word.length - breakIndex)
                    } catch (_: NumberFormatException) {
                        // Not numeric, use as-is
                    }

                    when (type) {
                        "M" -> insertNoun(stmt, word, NounGender.M)
                        "F" -> insertNoun(stmt, word, NounGender.F)
                        "N" -> insertNoun(stmt, word, NounGender.N)
                        "MF" -> insertNoun(stmt, word, NounGender.M)
                        "A" -> insertAdjective(stmt, word)
                        "V", "VT" -> insertVerb(stmt, word)
                        else -> { skipped++; return@forEachLine }
                    }
                    count++
                    if (count % 10000 == 0) {
                        stmt.executeBatch()
                        println("  Processed $count words...")
                    }
                }
            }
        }

        stmt.executeBatch()
        println("Loaded $count words ($skipped skipped)")
    }
}

private fun insertNoun(stmt: java.sql.PreparedStatement, word: String, gender: NounGender) {
    val noun = Noun(word, gender)
    stmt.setString(1, word)
    stmt.setString(2, "N")
    stmt.setString(3, gender.name.substring(0, 1))
    stmt.setInt(4, noun.syllables)
    stmt.setString(5, noun.rhyme)
    stmt.setString(6, word.first().lowercaseChar().toString())
    stmt.setString(7, noun.articulated)
    stmt.setNull(8, java.sql.Types.VARCHAR)
    stmt.setInt(9, WordUtils.computeSyllableNumber(noun.articulated))
    stmt.setInt(10, 4)
    stmt.setNull(11, java.sql.Types.SMALLINT)
    stmt.addBatch()
}

private fun insertAdjective(stmt: java.sql.PreparedStatement, word: String) {
    val adj = Adjective(word)
    stmt.setString(1, word)
    stmt.setString(2, "A")
    stmt.setNull(3, java.sql.Types.CHAR)
    stmt.setInt(4, adj.syllables)
    stmt.setString(5, adj.rhyme)
    stmt.setString(6, word.first().lowercaseChar().toString())
    stmt.setNull(7, java.sql.Types.VARCHAR)
    stmt.setString(8, adj.feminine)
    stmt.setNull(9, java.sql.Types.SMALLINT)
    stmt.setInt(10, 4)
    stmt.setInt(11, adj.feminineSyllables)
    stmt.addBatch()
}

private fun insertVerb(stmt: java.sql.PreparedStatement, word: String) {
    val verb = Verb(word)
    stmt.setString(1, word)
    stmt.setString(2, "V")
    stmt.setNull(3, java.sql.Types.CHAR)
    stmt.setInt(4, verb.syllables)
    stmt.setString(5, verb.rhyme)
    stmt.setString(6, word.first().lowercaseChar().toString())
    stmt.setNull(7, java.sql.Types.VARCHAR)
    stmt.setNull(8, java.sql.Types.VARCHAR)
    stmt.setNull(9, java.sql.Types.SMALLINT)
    stmt.setInt(10, 4)
    stmt.setNull(11, java.sql.Types.SMALLINT)
    stmt.addBatch()
}
