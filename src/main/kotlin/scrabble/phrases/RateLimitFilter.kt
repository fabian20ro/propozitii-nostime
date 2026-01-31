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
            requestContext.abortWith(
                Response.status(429)
                    .entity(mapOf("error" to "Too many requests. Try again later."))
                    .type(MediaType.APPLICATION_JSON)
                    .build()
            )
            return
        }

        cleanupStaleEntries(windowStart)
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
