package scrabble.phrases

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

@Provider
class CacheControlFilter : ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val uriInfo = requestContext.uriInfo ?: return
        val path = uriInfo.path ?: return
        if (path.startsWith("api/") && responseContext.status in 200..299) {
            responseContext.headers.putSingle("Cache-Control", "public, max-age=$MAX_AGE_SECONDS, must-revalidate")
        }
    }

    companion object {
        private const val MAX_AGE_SECONDS = 180
    }
}
