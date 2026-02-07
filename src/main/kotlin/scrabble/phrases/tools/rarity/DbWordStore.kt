package scrabble.phrases.tools.rarity

import java.sql.Connection
import java.sql.DriverManager

interface WordStore {
    fun fetchAllWords(): List<BaseWordRow>
    fun fetchAllWordLevels(): List<WordLevel>
    fun updateRarityLevels(updates: Map<Int, Int>)
}

class DbConnectionFactory(
    private val dbUrl: String = System.getenv("SUPABASE_DB_URL") ?: "jdbc:postgresql://localhost:5432/postgres",
    private val dbUser: String = System.getenv("SUPABASE_DB_USER") ?: "postgres",
    private val dbPassword: String = System.getenv("SUPABASE_DB_PASSWORD") ?: ""
) {
    fun <T> withConnection(block: (Connection) -> T): T {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            return block(conn)
        }
    }
}

class JdbcWordStore(
    private val connectionFactory: DbConnectionFactory
) : WordStore {

    override fun fetchAllWords(): List<BaseWordRow> {
        return connectionFactory.withConnection { conn ->
            queryRows(conn, "SELECT id, word, type FROM words ORDER BY id") { rs ->
                BaseWordRow(
                    wordId = rs.getInt("id"),
                    word = rs.getString("word"),
                    type = rs.getString("type")
                )
            }
        }
    }

    override fun fetchAllWordLevels(): List<WordLevel> {
        return connectionFactory.withConnection { conn ->
            queryRows(conn, "SELECT id, rarity_level FROM words ORDER BY id") { rs ->
                WordLevel(
                    wordId = rs.getInt("id"),
                    rarityLevel = rs.getInt("rarity_level")
                )
            }
        }
    }

    override fun updateRarityLevels(updates: Map<Int, Int>) {
        if (updates.isEmpty()) return

        connectionFactory.withConnection { conn ->
            withTransaction(conn) {
                conn.prepareStatement("UPDATE words SET rarity_level=? WHERE id=?").use { stmt ->
                    updates.forEach { (wordId, level) ->
                        stmt.setInt(1, level)
                        stmt.setInt(2, wordId)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
        }
    }

    private fun <T> queryRows(
        conn: Connection,
        sql: String,
        mapper: (java.sql.ResultSet) -> T
    ): List<T> {
        return conn.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(mapper(rs))
                    }
                }
            }
        }
    }

    private fun withTransaction(conn: Connection, block: () -> Unit) {
        conn.autoCommit = false
        try {
            block()
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }
}
