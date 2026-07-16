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

        val ip = extractIp(requestContext) ?: "unknown"

        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_MS

        val timestamps = requests.computeIfAbsent(ip) { CopyOnWriteArrayList() }
        timestamps.removeIf { it < windowStart }
        timestamps.add(now)

        if (timestamps.size > MAX_REQUESTS) {
            // Prefer server-supplied reset headers; fall back to window-based estimate.
            val retryAfterSeconds = calculateRetryAfterSeconds(ip, now)
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

    private fun calculateRetryAfterSeconds(ip: String, nowMs: Long): Int {
        val oldest = oldestTimestampFor(ip)
        val raw = (oldest + WINDOW_MS - nowMs).coerceAtLeast(1L) / 1000
        return maxOf(1, raw.toInt())
    }

    private fun oldestTimestampFor(ip: String): Long {
        val timestamps = requests[ip] ?: return 0L
        return timestamps.firstOrNull() ?: return 0L
    }

    private fun cleanupStaleEntries(windowStart: Long) {
        if (requests.size <= CLEANUP_THRESHOLD) return
        val staleKeys = requests.entries
            .filter { it.value.isEmpty() || it.value.all { ts -> ts < windowStart } }
            .map { it.key }
        staleKeys.forEach { requests.remove(it) }
    }

    private fun extractIp(context: ContainerRequestContext): String? {
        return context.getHeaderString("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
    }

    companion object {
        private const val WINDOW_MS = 60_000L
        private const val MAX_REQUESTS = 30
        private const val CLEANUP_THRESHOLD = 100
    }
}
