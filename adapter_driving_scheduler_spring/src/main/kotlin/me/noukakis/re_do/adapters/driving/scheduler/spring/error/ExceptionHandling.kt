package me.noukakis.re_do.adapters.driving.scheduler.spring.error

import io.sentry.Sentry
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.TegSchedulingException
import me.noukakis.re_do.scheduler.model.TegSchedulingError
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ApiError(val cause: String?, val errorId: String? = null)

@ControllerAdvice
class ExceptionHandling {

    private val logger = LoggerFactory.getLogger(ExceptionHandling::class.java)

    @ExceptionHandler(TegSchedulingException::class)
    fun handleTegSchedulingException(ex: TegSchedulingException): ResponseEntity<ApiError> = when (ex.error) {
        is TegSchedulingError.EmptyTegNotAllowed -> ResponseEntity(
            ApiError("Empty Task Execution Graphs are not allowed"),
            HttpStatus.BAD_REQUEST
        )
        is TegSchedulingError.NoStartingTaskFound -> ResponseEntity(
            ApiError("No starting task found in the Task Execution Graph"),
            HttpStatus.BAD_REQUEST
        )
        is TegSchedulingError.MissingArtefactProducer -> ResponseEntity(
            ApiError("Missing producer for artefact '${ex.error.artefactName}' required by task '${ex.error.taskName}'"),
            HttpStatus.BAD_REQUEST
        )
        is TegSchedulingError.CyclicDependencyDetected -> ResponseEntity(
            ApiError("Cyclic dependency detected among tasks: ${ex.error.cycle.joinToString(" -> ")}"),
            HttpStatus.BAD_REQUEST
        )
        is TegSchedulingError.TasksHaveTheSameName -> ResponseEntity(
            ApiError("Multiple tasks have the same name '${ex.error.taskName}'"),
            HttpStatus.BAD_REQUEST
        )
        is TegSchedulingError.TasksProduceSameArtefactName -> ResponseEntity(
            ApiError("Multiple tasks produce the same artefact '${ex.error.artefactName}': ${ex.error.taskNames.joinToString(", ")}"),
            HttpStatus.BAD_REQUEST
        )
        is TegSchedulingError.NotAllProducedArtefactsAreConsumed -> ResponseEntity(
            ApiError("Produced artefact '${ex.error.artefactName}' by task '${ex.error.producingTaskName}' is not consumed by any task"),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ApiError> {
        logger.error("Uncaught exception", ex)
        val sentryId = Sentry.captureException(ex)
        return ResponseEntity(ApiError(
            cause = "An unexpected error occurred",
            errorId = sentryId.toString()
        ), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}