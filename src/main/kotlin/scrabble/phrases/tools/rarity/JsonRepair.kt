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

    private val CLOSER_FOR = mapOf('"' to '"', '{' to '}', '[' to ']')

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

        walkJsonChars(input) { i, ch, inString ->
            if (!inString && ch == '.' && i > 0 && input[i - 1].isDigit()) {
                val next = input.getOrNull(i + 1)
                if (next == null || !next.isDigit()) {
                    result.append(".0")
                    return@walkJsonChars i + 1
                }
            }
            result.append(ch)
            i + 1
        }

        return result.toString()
    }

    /**
     * Strips `//` line comments outside quoted strings.
     */
    internal fun removeLineComments(input: String): String {
        val result = StringBuilder(input.length)

        walkJsonChars(input) { i, ch, inString ->
            if (!inString && ch == '/' && input.getOrNull(i + 1) == '/') {
                while (result.isNotEmpty() && result.last() == ' ') {
                    result.deleteCharAt(result.length - 1)
                }
                val lineEnd = input.indexOf('\n', i)
                return@walkJsonChars if (lineEnd == -1) input.length else lineEnd
            }
            result.append(ch)
            i + 1
        }

        return result.toString()
    }

    private val TRAILING_COMMA_BRACKET = Regex(",\\s*]")
    private val TRAILING_COMMA_BRACE = Regex(",\\s*}")

    /**
     * Removes trailing commas before `]` or `}`, ignoring whitespace between them.
     */
    internal fun removeTrailingCommas(input: String): String {
        return input
            .replace(TRAILING_COMMA_BRACKET, "]")
            .replace(TRAILING_COMMA_BRACE, "}")
    }

    /**
     * Walks the string tracking `{`, `[`, `"` nesting and appends missing closers
     * for truncated output. Handles escape sequences inside strings.
     */
    internal fun closeUnclosedStructures(input: String): String {
        val stack = ArrayDeque<Char>()
        var wasInString = false

        walkJsonChars(input) { i, ch, inString ->
            if (ch == '"') {
                if (!inString && wasInString) {
                    // closing quote
                    if (stack.lastOrNull() == '"') stack.removeLast()
                } else if (inString && !wasInString) {
                    // opening quote
                    stack.addLast('"')
                }
                wasInString = inString
                return@walkJsonChars i + 1
            }
            wasInString = inString

            if (!inString) {
                when (ch) {
                    '{' -> stack.addLast('{')
                    '[' -> stack.addLast('[')
                    '}' -> if (stack.lastOrNull() == '{') stack.removeLast()
                    ']' -> if (stack.lastOrNull() == '[') stack.removeLast()
                }
            }
            i + 1
        }

        if (stack.isEmpty()) return input

        val suffix = buildString {
            for (opener in stack.reversed()) {
                append(CLOSER_FOR.getOrDefault(opener, ""))
            }
        }

        return input + suffix
    }
}
