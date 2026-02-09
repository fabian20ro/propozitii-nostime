package scrabble.phrases.tools.rarity

data class LevelTransition(
    val fromLevel: Int,
    val toLevel: Int,
    val fromLevelUpper: Int? = null
)

fun parseStep5Transitions(raw: String?): List<LevelTransition> {
    val input = raw?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_REBALANCE_TRANSITIONS
    val parsed = input.split(",")
        .map { token ->
            val parts = token.trim().split(":")
            require(parts.size == 2) {
                "Invalid transition token '$token'. Expected format from:to (example: 2:1 or 2-3:2)"
            }
            val fromToken = parts[0].trim()
            val to = parts[1].trim().toIntOrNull()
                ?: error("Invalid transition target level in '$token'")
            if (fromToken.contains("-")) {
                val range = fromToken.split("-")
                require(range.size == 2) {
                    "Invalid transition source range '$fromToken' in '$token'. Expected lower-upper (example: 2-3:2)"
                }
                val fromLower = range[0].trim().toIntOrNull()
                    ?: error("Invalid transition lower source level in '$token'")
                val fromUpper = range[1].trim().toIntOrNull()
                    ?: error("Invalid transition upper source level in '$token'")
                requireValidStep5PairTransition(fromLower, fromUpper, to)
                LevelTransition(fromLevel = fromLower, toLevel = to, fromLevelUpper = fromUpper)
            } else {
                val from = fromToken.toIntOrNull()
                    ?: error("Invalid transition source level in '$token'")
                requireValidStep5Transition(from, to)
                LevelTransition(fromLevel = from, toLevel = to)
            }
        }
    validateTransitionSet(parsed)
    return parsed.distinct().sortedWith(compareBy<LevelTransition> { it.fromLevel }.thenBy { it.fromLevelUpper ?: it.fromLevel })
}

fun requireValidStep5Transition(fromLevel: Int, toLevel: Int) {
    val validRange = fromLevel in 1..5 && toLevel in 1..5
    val validRelation = toLevel == fromLevel - 1 || toLevel == fromLevel
    val invalidTopSelfSplit = fromLevel == 5 && toLevel == 5
    require(validRange && validRelation && !invalidTopSelfSplit) {
        "Invalid transition '$fromLevel:$toLevel'. Allowed: one-step downgrade (ex: 3:2) " +
            "or keep+promote split (ex: 2:2). 5:5 is not allowed."
    }
}

fun requireValidStep5PairTransition(fromLower: Int, fromUpper: Int, toLevel: Int) {
    require(fromLower in 1..5 && fromUpper in 1..5 && toLevel in 1..5) {
        "Invalid pair transition '$fromLower-$fromUpper:$toLevel'. Levels must be in range 1..5."
    }
    require(fromUpper == fromLower + 1) {
        "Invalid pair transition '$fromLower-$fromUpper:$toLevel'. Source levels must be consecutive."
    }
    require(toLevel == fromLower || toLevel == fromUpper) {
        "Invalid pair transition '$fromLower-$fromUpper:$toLevel'. Target must be one of the source levels."
    }
}

fun validateTransitionSet(transitions: List<LevelTransition>) {
    require(transitions.isNotEmpty()) { "Step 5 requires at least one transition." }
    val duplicateSourceLevels = transitions
        .flatMap { transition -> transition.sourceLevels() }
        .groupBy { it }
        .filterValues { it.size > 1 }
        .keys
    require(duplicateSourceLevels.isEmpty()) {
        "Step 5 transitions must not overlap source levels. Duplicates: ${duplicateSourceLevels.sorted().joinToString(",")}"
    }
}

fun LevelTransition.otherLevel(): Int {
    if (fromLevelUpper != null) {
        return sourceLevels().first { it != toLevel }
    }
    return if (toLevel == fromLevel) (toLevel + 1).coerceAtMost(5) else fromLevel
}

fun LevelTransition.sourceLevels(): List<Int> {
    return if (fromLevelUpper == null) listOf(fromLevel) else listOf(fromLevel, fromLevelUpper)
}

fun LevelTransition.describeSources(): String {
    return if (fromLevelUpper == null) fromLevel.toString() else "$fromLevel-$fromLevelUpper"
}
