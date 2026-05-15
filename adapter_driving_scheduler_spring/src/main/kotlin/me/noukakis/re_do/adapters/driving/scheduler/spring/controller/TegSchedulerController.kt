package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.ScheduleTegRequest
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.ScheduleTegResponse
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.TEGEventDTO
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.ApiError
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.StreamTegEventsException
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.TegSchedulingException
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.TegEventListener
import me.noukakis.re_do.scheduler.service.StreamTegEventsCommand
import me.noukakis.re_do.scheduler.service.StreamTegEventsUseCase
import me.noukakis.re_do.scheduler.service.TEGScheduler
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/teg")
class TegSchedulerController(
    private val tegScheduler: TEGScheduler,
    private val streamTegEventsUseCase: StreamTegEventsUseCase,
) {

    @PostMapping("/schedule")
    fun scheduleTeg(
        @RequestHeader("X-Auth-Principal") sub: String,
        @RequestHeader("X-Auth-Roles") roles: List<String>,
        @Valid @RequestBody request: ScheduleTegRequest,
    ): ResponseEntity<ScheduleTegResponse> = ResponseEntity.ok(
        tegScheduler.scheduleTeg(request.toCommand(sub, roles)).fold(
            { error -> throw TegSchedulingException(error) },
            { ScheduleTegResponse(tegId = it) },
        ),
    )

    @Operation(
        summary = "Stream TEG events as Server-Sent Events",
        description = "Replays the TEG's event history then continues live. The connection closes after a " +
            "terminal event (NoMoreTasksToSchedule or TEGFailed). Only the original submitter " +
            "(matched via X-Auth-Principal) may subscribe.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Event stream. Each SSE 'data:' line is a JSON-encoded TEGEventDTO.",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(implementation = TEGEventDTO::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "403",
                description = "The caller is not the submitter of this TEG.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiError::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "No TEG with this id was found.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ApiError::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{tegId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamTegEvents(
        @PathVariable tegId: String,
        @RequestHeader("X-Auth-Principal") sub: String,
    ): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val listener = object : TegEventListener {
            override fun onEvent(event: TEGEvent) {
                try {
                    emitter.send(TEGEventDTO.fromDomain(event))
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            }
            override fun onComplete() = emitter.complete()
            override fun onError(t: Throwable) = emitter.completeWithError(t)
        }
        return streamTegEventsUseCase.execute(StreamTegEventsCommand(tegId, sub), listener).fold(
            { error -> throw StreamTegEventsException(error) },
            { handle ->
                emitter.onCompletion { handle.close() }
                emitter.onTimeout { handle.close() }
                emitter.onError { handle.close() }
                emitter
            },
        )
    }
}
