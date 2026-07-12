package scrabble.phrases

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Provider
class RateLimitFilter : ContainerRequestFilter {

    private val requests = ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()

    override fun filter(requestContext: ContainerRequestContext) {
        val path = requestContext.uriInfo.path
        if (!path.startsWith("api/")) return

        val ip = requestContext.getHeaderString("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?: "unknown"

        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_MS

        val timestamps = requests.computeIfAbsent(ip) { CopyOnWriteArrayList() }
        timestamps.removeIf { it < windowStart }
        timestamps.add(now)

        if (timestamps.size > MAX_REQUESTS) {
            // Prefer server-supplied reset headers; fall back to window-based estimate.
            val retryAfterSeconds = calculateRetryAfterSeconds(requestContext, System.currentTimeMillis())
            requestContext.abortWith(
                Response.status(429)
                    .header("Retry-After", retryAfterSeconds)
                    .entity(mapOf("error" to "Too many requests. Try again later."))
                    .type(MediaType.APPLICATION_JSON)
                    .build()
            )
            timestamps.clear()
            return
        }

        cleanupStaleEntries(windowStart)
    }

    private fun calculateRetryAfterSeconds(context: ContainerRequestContext, nowMs: Long): Int {
        // Prefer server-supplied reset headers over the generic retry_after field.
        val reset = context.getHeaderString("X-RateLimit-Reset") ?: context.getHeaderString("x-ratelimit-reset")
        if (reset != null) {
            try {
                val resetEpochSec = reset.toLong()
                return maxOf(1, ((resetEpochSec * 1000 - nowMs + 999) / 1000).toInt())
            } catch (_: Exception) { /* fall through */ }
        }

        val retryAfter = context.getHeaderString("retry_after") ?: context.getHeaderString("Retry-After")
        if (retryAfter != null) {
            try {
                val secs = retryAfter.toLong()
                return maxOf(1, secs.toInt())
            } catch (_: Exception) {
                val fallback = retryAfter.toLongOrNull()
                if (fallback != null && fallback > 0) return maxOf(1, fallback.toInt())
            }
        }

        // Fall back to window-based estimate using oldest tracked timestamp.
        val ip = context.getHeaderString("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: return WINDOW_MS.toInt() / 1000
        val timestamps = requests[ip] ?: return WINDOW_MS.toInt() / 1000
        val oldest = timestamps.firstOrNull() ?: return WINDOW_MS.toInt() / 1000
        return maxOf(1, ((oldest + WINDOW_MS) - nowMs) / 1000).toInt()
    }

    private fun cleanupStaleEntries(windowStart: Long) {
        if (requests.size <= CLEANUP_THRESHOLD) return
        val staleKeys = requests.entries
            .filter { it.value.isEmpty() || it.value.all { ts -> ts < windowStart } }
            .map { it.key }
        staleKeys.forEach { requests.remove(it) }
    }

    companion object {
        private const val WINDOW_MS = 60_000L
        private const val MAX_REQUESTS = 30
        private const val CLEANUP_THRESHOLD = 100
    }
}
