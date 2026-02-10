package scrabble.phrases.tools.rarity

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.roundToInt

fun resolveStep(args: Array<String>): Pair<String, List<String>> {
    val stepFromProperty = System.getProperty("rarity.step")
    if (!stepFromProperty.isNullOrBlank()) {
        return stepFromProperty to args.toList()
    }

    if (args.isEmpty()) {
        error("Missing step. Usage: RarityPipelineKt <step1|step2|step3|step4|step5> [options]")
    }

    return args[0] to args.drop(1)
}

fun parseArgs(args: List<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0

    while (i < args.size) {
        val token = args[i]
        if (!token.startsWith("--")) {
            i++
            continue
        }

        val raw = token.removePrefix("--")
        if (raw.contains("=")) {
            val (key, value) = raw.split("=", limit = 2)
            out[key] = value
            i++
            continue
        }

        val value = if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
            i += 2
            args[i - 1]
        } else {
            i += 1
            "true"
        }

        out[raw] = value
    }

    return out
}

fun requiredOption(options: Map<String, String>, key: String): String =
    options[key]?.takeIf { it.isNotBlank() }
        ?: error("Missing required option --$key")

fun sanitizeRunSlug(raw: String): String {
    val normalized = raw.trim().lowercase().replace('-', '_')
    require(normalized.matches(Regex("[a-z0-9_]{1,40}"))) {
        "Invalid run slug '$raw'. Allowed pattern: [a-z0-9_]{1,40}"
    }
    return normalized
}

fun median(values: List<Int>): Int {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        ((sorted[mid - 1] + sorted[mid]) / 2.0).roundToInt()
    }
}

fun loadPrompt(filePath: String?, defaultFilePath: String): String {
    val path = if (!filePath.isNullOrBlank()) Paths.get(filePath) else Paths.get(defaultFilePath)
    require(Files.exists(path)) { "Prompt file does not exist: ${path.toAbsolutePath()}" }
    val content = Files.readString(path).trim()
    require(content.isNotBlank()) { "Prompt file is empty: ${path.toAbsolutePath()}" }
    return content
}

fun ensureRarityOutputDir(): Path {
    val outputDir = Paths.get("build", "rarity")
    Files.createDirectories(outputDir)
    return outputDir
}
