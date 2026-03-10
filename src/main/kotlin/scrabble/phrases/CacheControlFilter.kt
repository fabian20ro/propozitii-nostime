package scrabble.phrases

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

@Provider
class CacheControlFilter : ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext, responseContext: ContainerResponseContext) {
        val path = requestContext.uriInfo.path
        if (path.startsWith("api/")) {
            responseContext.headers.putSingle("Cache-Control", "max-age=$MAX_AGE_SECONDS")
        }
    }

    companion object {
        private const val MAX_AGE_SECONDS = 180
    }
}
