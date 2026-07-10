package scrabble.phrases.words

enum class NounGender {
    M, F, N;

    companion object {
        fun fromCode(code: String?): NounGender? {
            if (code.isNullOrEmpty()) return null
            return try {
                valueOf(code.uppercase())
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        fun toCode(gender: NounGender): String = gender.name.first().toString()
    }
}
