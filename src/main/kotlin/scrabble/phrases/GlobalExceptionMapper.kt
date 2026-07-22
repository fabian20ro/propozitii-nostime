package scrabble.phrases

import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.jboss.logging.Logger

@Provider
class GlobalExceptionMapper : ExceptionMapper<Exception> {

    private val log = Logger.getLogger(GlobalExceptionMapper::class.java)

    @Inject
    lateinit var uriInfo: UriInfo

    override fun toResponse(exception: Exception): Response {
        if (exception is WebApplicationException && responseContextStatusOk(exception)) {
            return exception.response
        }
        val stackTrace = exception.stackTraceToString()
        log.error("Unhandled exception on ${uriInfo.path} — ${exception.javaClass.name}: ${exception.message}\n$stackTrace")
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(mapOf("error" to "Internal server error"))
            .type(MediaType.APPLICATION_JSON)
            .build()
    }

    private fun responseContextStatusOk(e: WebApplicationException): Boolean {
        val r = e.response ?: return false
        return r.status in 200..399
    }
}
