package me.noukakis.re_do.web.adapter_primary_spring.controller

import jakarta.validation.Valid
import me.noukakis.re_do.scheduler.service.TEGScheduler
import me.noukakis.re_do.web.adapter_primary_spring.dto.ScheduleTegRequest
import me.noukakis.re_do.web.adapter_primary_spring.dto.ScheduleTegResponse
import me.noukakis.re_do.web.adapter_primary_spring.error.exceptions.TegSchedulingException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/teg")
class TegSchedulerController(
    private val tegScheduler: TEGScheduler
) {

    @PostMapping("/schedule")
    fun scheduleTeg(
        @Valid @RequestBody request: ScheduleTegRequest
    ): ResponseEntity<ScheduleTegResponse> {
        return ResponseEntity.ok(tegScheduler.scheduleTeg(request.toCommand()).fold(
            { error -> throw TegSchedulingException(error) },
            { ScheduleTegResponse(tegId = it) },
        ))
    }
}

