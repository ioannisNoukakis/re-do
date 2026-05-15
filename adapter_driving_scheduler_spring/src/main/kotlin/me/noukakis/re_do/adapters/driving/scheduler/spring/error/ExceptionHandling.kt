package me.noukakis.re_do.adapters.driving.scheduler.spring.error

import io.sentry.Sentry
import jakarta.servlet.http.HttpServletResponse
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.StreamTegEventsException
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.TegSchedulingException
import me.noukakis.re_do.scheduler.model.StreamTegEventsError
import me.noukakis.re_do.scheduler.model.TegSchedulingError
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.support.MissingServletRequestPartException
import tools.jackson.databind.ObjectMapper

data class ApiError(val cause: String?, val errorId: String? = null)

@ControllerAdvice
class ExceptionHandling(
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(ExceptionHandling::class.java)

    @ExceptionHandler(TegSchedulingException::class)
    fun handleTegSchedulingException(ex: TegSchedulingException): ResponseEntity<ApiError> = when (ex.error) {
        is TegSchedulingError.EmptyTegNotAllowed -> ResponseEntity(
            ApiError("Empty Task Execution Graphs are not allowed"),
            HttpStatus.BAD_REQUEST,
        )

        is TegSchedulingError.NoStartingTaskFound -> ResponseEntity(
            ApiError("No starting task found in the Task Execution Graph"),
            HttpStatus.BAD_REQUEST,
        )

        is TegSchedulingError.MissingArtefactProducer -> ResponseEntity(
            ApiError("Missing producer for artefact '${ex.error.artefactName}' required by task '${ex.error.taskName}'"),
            HttpStatus.BAD_REQUEST,
        )

        is TegSchedulingError.CyclicDependencyDetected -> ResponseEntity(
            ApiError("Cyclic dependency detected among tasks: ${ex.error.cycle.joinToString(" -> ")}"),
            HttpStatus.BAD_REQUEST,
        )

        is TegSchedulingError.TasksHaveTheSameName -> ResponseEntity(
            ApiError("Multiple tasks have the same name '${ex.error.taskName}'"),
            HttpStatus.BAD_REQUEST,
        )

        is TegSchedulingError.TasksProduceSameArtefactName -> ResponseEntity(
            ApiError("Multiple tasks produce the same artefact '${ex.error.artefactName}': ${ex.error.taskNames.joinToString(", ")}"),
            HttpStatus.BAD_REQUEST,
        )

        is TegSchedulingError.NotAllProducedArtefactsAreConsumed -> ResponseEntity(
            ApiError("Produced artefact '${ex.error.artefactName}' by task '${ex.error.producingTaskName}' is not consumed by any task"),
            HttpStatus.BAD_REQUEST,
        )
    }

    @ExceptionHandler(StreamTegEventsException::class)
    fun handleStreamTegEventsException(
        ex: StreamTegEventsException,
        response: HttpServletResponse,
    ) {
        val (status, message) = when (ex.error) {
            is StreamTegEventsError.TegNotFound ->
                HttpStatus.NOT_FOUND to "Task Execution Graph not found"

            is StreamTegEventsError.Forbidden ->
                HttpStatus.FORBIDDEN to "You are not authorised to access this Task Execution Graph"
        }
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ApiError(message))
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(ex: MissingRequestHeaderException): ResponseEntity<ApiError> = ResponseEntity(ApiError("Missing required header: ${ex.headerName}"), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingServletRequestPart(ex: MissingServletRequestPartException): ResponseEntity<ApiError> = ResponseEntity(ApiError("Missing required request part: ${ex.requestPartName}"), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> = ResponseEntity(ApiError("Validation failed: ${ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }}"), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> = ResponseEntity(ApiError("Validation failed: ${ex.message}"), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ApiError> {
        logger.error("Uncaught exception", ex)
        val sentryId = Sentry.captureException(ex)
        return ResponseEntity(
            ApiError(
                cause = "An unexpected error occurred",
                errorId = sentryId.toString(),
            ),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )
    }
}
