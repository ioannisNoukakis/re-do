package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import jakarta.validation.Valid
import me.noukakis.re_do.scheduler.service.TEGScheduler
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.ScheduleTegRequest
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.ScheduleTegResponse
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.TegSchedulingException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/teg")
class TegSchedulerController(
    private val tegScheduler: TEGScheduler
) {

    @PostMapping("/schedule")
    fun scheduleTeg(
        @RequestHeader("X-Auth-Principal") sub: String,
        @RequestHeader("X-Auth-Roles") roles: List<String>,
        @Valid @RequestBody request: ScheduleTegRequest
    ): ResponseEntity<ScheduleTegResponse> {
        return ResponseEntity.ok(tegScheduler.scheduleTeg(request.toCommand(sub, roles)).fold(
            { error -> throw TegSchedulingException(error) },
            { ScheduleTegResponse(tegId = it) },
        ))
    }
}

