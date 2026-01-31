package scrabble.phrases.words

object WordUtils {

    private val fixes = mapOf("'" to "")

    private val tongs = arrayOf(
        "iai", "eau", "iau", "oai", "ioa",
        "ia", "oa", "ea", "ua", "\u00e2u",
        "ou", "ei", "ai", "oi", "ie", "ui"
    )

    fun capitalizeFirstLetter(sentence: String?): String? {
        if (sentence.isNullOrEmpty()) return sentence
        return sentence.substring(0, 1).uppercase() + sentence.substring(1)
    }

    fun computeSyllableNumber(word: String): Int {
        val chars = word.toCharArray()
        replaceTongsWithChar(chars)
        return chars.count { isVowel(it) }
    }

    fun computeRhyme(name: String): String =
        name.substring(maxOf(0, name.length - 3))

    fun fixWordCharacters(word: String): String {
        var result = word
        for ((key, value) in fixes) {
            result = result.replace(key, value)
        }
        return result
    }

    private fun replaceTongsWithChar(chars: CharArray) {
        val clength = chars.size
        for (tong in tongs) {
            val tch = tong.toCharArray()
            var i = 0
            while (i < clength - tong.length + 1) {
                if (tch.size == 3) {
                    if (tch[0] == chars[i] && tch[1] == chars[i + 1] && tch[2] == chars[i + 2]) {
                        chars[i] = 'z'
                        chars[i + 2] = 'z'
                        i += 2
                    }
                } else if (tch[0] == chars[i] && tch[1] == chars[i + 1]) {
                    if (i > 0) {
                        if (!isVowel(chars[i - 1])) {
                            chars[i + 1] = 'z'
                        } else {
                            chars[i] = 'z'
                        }
                        i++
                    }
                }
                i++
            }
        }
        if (clength >= 2 && isVowel(chars[clength - 1]) && isVowel(chars[clength - 2])
            && chars[clength - 1] != chars[clength - 2]
        ) {
            chars[clength - 2] = 'z'
        }
    }

    private fun isVowel(c: Char): Boolean =
        c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' ||
            c == '\u0103' || c == '\u00e2' || c == '\u00ee'
}
