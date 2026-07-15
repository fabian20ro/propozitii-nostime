package scrabble.phrases.words

enum class NounGender {
    M, F, N;

    companion object {
        private val VALID_CODES: Set<String> = values().map { it.name.uppercase() }.toSet()

        /** Returns true if [code] (any case) maps to a known NounGender. */
        fun isValidCode(code: String?): Boolean = code != null && !code.isEmpty() && code.uppercase() in VALID_CODES

        fun fromCode(code: String?): NounGender? {
            val upper = code?.uppercase() ?: return null
            if (upper !in VALID_CODES) return null
            return valueOf(upper)
        }

        fun toCode(gender: NounGender): String = gender.name.first().toString()
    }
}
