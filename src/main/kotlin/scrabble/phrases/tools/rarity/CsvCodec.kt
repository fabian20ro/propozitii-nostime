package scrabble.phrases.tools.rarity

import java.io.BufferedWriter
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CsvFormatException(message: String) : IllegalStateException(message)

data class CsvRecord(
    val lineNumber: Int,
    val values: List<String>
)

data class CsvTable(
    val headers: List<String>,
    val records: List<CsvRecord>
)

class CsvCodec {
    fun readTable(path: Path): CsvTable {
        require(Files.exists(path)) { "CSV file not found: ${path.toAbsolutePath()}" }
        val lines = Files.readAllLines(path, Charsets.UTF_8)
        require(lines.isNotEmpty()) { "CSV file is empty: ${path.toAbsolutePath()}" }

        val headers = parseLine(lines.first(), lineNumber = 1)
        require(headers.isNotEmpty()) { "CSV ${path.toAbsolutePath()} has empty header row" }

        val records = lines.drop(1)
            .mapIndexedNotNull { index, raw ->
                val lineNumber = index + 2
                if (raw.isBlank()) return@mapIndexedNotNull null
                val values = parseLine(raw, lineNumber)
                if (values.size != headers.size) {
                    throw CsvFormatException(
                        "CSV ${path.toAbsolutePath()} line $lineNumber has ${values.size} columns, expected ${headers.size}"
                    )
                }
                CsvRecord(lineNumber = lineNumber, values = values)
            }

        return CsvTable(headers = headers, records = records)
    }

    fun writeTable(path: Path, headers: List<String>, rows: List<List<String>>) {
        ensureParent(path)
        Files.newBufferedWriter(path, Charsets.UTF_8).use { writer ->
            writer.writeRow(headers)
            rows.forEach { row ->
                require(row.size == headers.size) {
                    "Attempted to write row with ${row.size} columns, expected ${headers.size}"
                }
                writer.writeRow(row)
            }
        }
    }

    fun writeTableAtomic(path: Path, headers: List<String>, rows: List<List<String>>) {
        ensureParent(path)
        val tempFile = path.resolveSibling("${path.fileName}.tmp")
        writeTable(tempFile, headers, rows)
        try {
            Files.move(
                tempFile,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun parseLine(line: String, lineNumber: Int): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"')
                    i += 2
                }

                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }

                c == ',' && !inQuotes -> {
                    out.add(cell.toString())
                    cell.setLength(0)
                    i++
                }

                else -> {
                    cell.append(c)
                    i++
                }
            }
        }

        if (inQuotes) {
            throw CsvFormatException("Malformed CSV at line $lineNumber: unclosed quoted field")
        }

        out.add(cell.toString())
        return out
    }

    fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun BufferedWriter.writeRow(values: List<String>) {
        write(values.joinToString(",") { escape(it) })
        newLine()
    }

    private fun ensureParent(path: Path) {
        path.parent?.let { Files.createDirectories(it) }
    }
}
