package scrabble.phrases.tools.rarity

/**
 * Best-effort repair of malformed JSON produced by language models.
 *
 * Handles the most common LM output failures observed in production:
 * - Trailing decimal points: `0.` -> `0.0`
 * - Trailing commas before `]` or `}`
 * - Line comments (`// ...`)
 * - Unclosed strings, objects, and arrays (truncated output)
 */
object JsonRepair {

    fun repair(raw: String): String {
        val s1 = removeLineComments(raw)
        val s2 = fixTrailingDecimalPoints(s1)
        val s3 = closeUnclosedStructures(s2)
        return removeTrailingCommas(s3)
    }

    /**
     * Fixes `0.` (digit-dot not followed by digit) outside quoted strings.
     * E.g. `"confidence": 0.}` -> `"confidence": 0.0}`
     */
    internal fun fixTrailingDecimalPoints(input: String): String {
        val result = StringBuilder(input.length + 16)
        var inString = false
        var escaped = false
        var i = 0

        while (i < input.length) {
            val ch = input[i]

            if (escaped) {
                result.append(ch)
                escaped = false
                i++
                continue
            }

            if (ch == '\\' && inString) {
                result.append(ch)
                escaped = true
                i++
                continue
            }

            if (ch == '"') {
                inString = !inString
                result.append(ch)
                i++
                continue
            }

            if (!inString && ch == '.' && i > 0 && input[i - 1].isDigit()) {
                val next = input.getOrNull(i + 1)
                if (next == null || !next.isDigit()) {
                    result.append(".0")
                    i++
                    continue
                }
            }

            result.append(ch)
            i++
        }

        return result.toString()
    }

    /**
     * Strips `//` line comments outside quoted strings.
     */
    internal fun removeLineComments(input: String): String {
        val result = StringBuilder(input.length)
        var inString = false
        var escaped = false
        var i = 0

        while (i < input.length) {
            val ch = input[i]

            if (escaped) {
                result.append(ch)
                escaped = false
                i++
                continue
            }

            if (ch == '\\' && inString) {
                result.append(ch)
                escaped = true
                i++
                continue
            }

            if (ch == '"') {
                inString = !inString
                result.append(ch)
                i++
                continue
            }

            if (!inString && ch == '/' && input.getOrNull(i + 1) == '/') {
                // strip trailing whitespace before the comment
                while (result.isNotEmpty() && result.last() == ' ') {
                    result.deleteCharAt(result.length - 1)
                }
                val lineEnd = input.indexOf('\n', i)
                if (lineEnd == -1) break
                i = lineEnd
                continue
            }

            result.append(ch)
            i++
        }

        return result.toString()
    }

    /**
     * Removes trailing commas before `]` or `}`, ignoring whitespace between them.
     */
    internal fun removeTrailingCommas(input: String): String {
        return input
            .replace(Regex(",\\s*]"), "]")
            .replace(Regex(",\\s*}"), "}")
    }

    /**
     * Walks the string tracking `{`, `[`, `"` nesting and appends missing closers
     * for truncated output. Handles escape sequences inside strings.
     */
    internal fun closeUnclosedStructures(input: String): String {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false

        for (ch in input) {
            if (escaped) {
                escaped = false
                continue
            }

            if (ch == '\\' && inString) {
                escaped = true
                continue
            }

            if (ch == '"') {
                if (inString) {
                    inString = false
                    if (stack.lastOrNull() == '"') stack.removeLast()
                } else {
                    inString = true
                    stack.addLast('"')
                }
                continue
            }

            if (inString) continue

            when (ch) {
                '{' -> stack.addLast('{')
                '[' -> stack.addLast('[')
                '}' -> if (stack.lastOrNull() == '{') stack.removeLast()
                ']' -> if (stack.lastOrNull() == '[') stack.removeLast()
            }
        }

        if (stack.isEmpty()) return input

        val suffix = buildString {
            for (opener in stack.reversed()) {
                append(
                    when (opener) {
                        '"' -> '"'
                        '{' -> '}'
                        '[' -> ']'
                        else -> ""
                    }
                )
            }
        }

        return input + suffix
    }
}
