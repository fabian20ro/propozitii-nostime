package scrabble.phrases.tools.rarity

/**
 * Walks a string character by character, tracking JSON quoted-string and
 * backslash-escape state. [onChar] receives the current index, the character,
 * and whether we are inside a quoted string. It must return the next index to
 * process (usually `i + 1`; returning a larger value skips characters).
 *
 * Shared by [JsonRepair] and [LmStudioResponseParser][scrabble.phrases.tools.rarity.lmstudio.LmStudioResponseParser]
 * to avoid duplicating the inString/escaped state machine.
 */
inline fun walkJsonChars(
    input: String,
    startIndex: Int = 0,
    onChar: (index: Int, ch: Char, inString: Boolean) -> Int
) {
    var inString = false
    var escaped = false
    var i = startIndex

    while (i < input.length) {
        val ch = input[i]

        if (escaped) {
            i = onChar(i, ch, true)
            escaped = false
            continue
        }

        if (ch == '\\' && inString) {
            i = onChar(i, ch, true)
            escaped = true
            continue
        }

        if (ch == '"') {
            inString = !inString
            i = onChar(i, ch, inString)
            continue
        }

        i = onChar(i, ch, inString)
    }
}
