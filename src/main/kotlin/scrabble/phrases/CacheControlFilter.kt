package scrabble.phrases

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider
import java.util.regex.Pattern

@Provider
class CacheControlFilter : ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val uriInfo = requestContext.uriInfo ?: return
        val path = uriInfo.path ?: return
        if (path.startsWith("api/") && responseContext.status in 200..299) {
            when {
                isDiagnosticEndpoint(path) -> applyNoCacheHeaders(responseContext)
                else -> responseContext.headers.putSingle(
                    "Cache-Control", "public, max-age=$MAX_AGE_SECONDS, must-revalidate"
                )
            }
        }
    }

    private fun applyNoCacheHeaders(context: ContainerResponseContext) {
        context.headers.putSingle("Cache-Control", "no-cache, no-store")
    }

    private fun isDiagnosticEndpoint(path: String): Boolean {
        return DIAGNOSTIC_PATTERN.matcher("/$path").matches()
    }

    companion object {
        private const val MAX_AGE_SECONDS = 180

        /** Matches health-check and diagnostic endpoints that should never be cached. */
        private val DIAGNOSTIC_PATTERN = Pattern.compile("^/api/(health|ping|ready|live)$")
    }
}
