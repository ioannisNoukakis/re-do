package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.DurationDTO
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.ScheduleTegRequest
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.ScheduleTegResponse
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.TegArtefactDefinitionDTO
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.TegArtefactTypeDTO
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.TegTaskDTO
import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.TemporalUnitDTO
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.ApiError
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody


const val IDENTITY_SUB = "user-123"
const val IDENTITY_ROLES = "scheduler-user,role2"

// https://spring.io/guides/gs/testing-web
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureRestTestClient
class TegSchedulerControllerTest {

    @Autowired
    private lateinit var restClient: RestTestClient

    @Test
    fun `should schedule TEG successfully with valid request`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    name = "task1"
                    arguments = listOf("arg1", "arg2")
                    outputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "output1",
                            type = TegArtefactTypeDTO.STRING_VALUE
                        )
                    )
                },
                tegTaskDTO {
                    name = "task2"
                    inputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "output1",
                            type = TegArtefactTypeDTO.STRING_VALUE
                        )
                    )
                },
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .body(request)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody<ScheduleTegResponse>()
            .value { response ->
                assert(response?.tegId != null) { "tegId should not be null" }
            }
    }

    @Test
    fun `should report invalid requests with bad request status`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    name = "task1"
                    inputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "input1",
                            type = TegArtefactTypeDTO.STRING_VALUE
                        )
                    )
                },
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ApiError>()
            .value { apiError ->
                assert(apiError?.cause == "Missing producer for artefact 'input1' required by task 'task1'") {
                    "Expected error cause to be 'Missing producer for artefact 'input1' required by task 'task1'', but was '${apiError?.cause}'"
                }
            }
    }

    @Test
    fun `should return bad request when tasks list is empty`() {
        val request = ScheduleTegRequest(tasks = emptyList())

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when tasks list is null`() {
        val request = """
            {
            }
        """.trimIndent()

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when task name is blank`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO { name = "" })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when task name is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO { name = null })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when task implementation name is blank`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO { implementationName = "" })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when task implementation name is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO { implementationName = null })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when timeout is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO { timeout = null })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when timeout amount is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO {
                timeout = DurationDTO(
                    amount = null,
                    temporalUnit = TemporalUnitDTO.SECONDS
                )
            })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when timeout temporal unit is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(tegTaskDTO {
                timeout = DurationDTO(
                    amount = 30,
                    temporalUnit = null,
                )
            })
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when artefact name is blank`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    inputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "",
                            type = TegArtefactTypeDTO.STRING_VALUE
                        )
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when artefact name is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    inputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = null,
                            type = TegArtefactTypeDTO.STRING_VALUE
                        )
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }


    @Test
    fun `should return bad request when artefact type is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    inputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "input1",
                            type = null
                        )
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when inputs list is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    inputs = null
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when outputs list is null`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    outputs = null
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @ParameterizedTest
    @EnumSource(TemporalUnitDTO::class)
    fun `should accept all valid temporal units`(temporalUnit: TemporalUnitDTO) {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    timeout = DurationDTO(
                        amount = 30,
                        temporalUnit = temporalUnit
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return bad request when temporal unit is invalid`() {
        val request = """
            {
                "tasks": [
                    {
                        "name": "task1",
                        "inputs": [],
                        "outputs": [],
                        "arguments": [],
                        "timeout": {
                            "amount": 30,
                            "temporalUnit": "INVALID_UNIT"
                        }
                    }
                ]
            }
        """.trimIndent()

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when artefact type is invalid`() {
        val request = """
            {
                "tasks": [
                    {
                        "name": "task1",
                        "inputs": [],
                        "outputs": [
                            {
                                "name": "output1",
                                "type": "INVALID_TYPE"
                            }
                        ],
                        "arguments": [],
                        "timeout": {
                            "amount": 30,
                            "temporalUnit": "SECONDS"
                        }
                    }
                ]
            }
        """.trimIndent()

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should accept FILE artefact type`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    name = "task1"
                    outputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "output1",
                            type = TegArtefactTypeDTO.FILE
                        )
                    )
                },
                tegTaskDTO {
                    name = "task2"
                    inputs = listOf(
                        TegArtefactDefinitionDTO(
                            name = "output1",
                            type = TegArtefactTypeDTO.FILE
                        )
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return bad request when timeout amount is not positive`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    timeout = DurationDTO(
                        amount = 0,
                        temporalUnit = TemporalUnitDTO.SECONDS
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when timeout amount is negative`() {
        val request = ScheduleTegRequest(
            tasks = listOf(
                tegTaskDTO {
                    timeout = DurationDTO(
                        amount = -1,
                        temporalUnit = TemporalUnitDTO.SECONDS
                    )
                }
            )
        )

        restClient.post()
            .uri("/api/v1/teg/schedule")
            .header("X-Auth-Principal", IDENTITY_SUB)
            .header("X-Auth-Roles", IDENTITY_ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when the sub header is missing`() {
        val request = ScheduleTegRequest(
            tasks = listOf()
        )

        restClient.post()
        .uri("/api/v1/teg/schedule")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return bad request when the roles header is missing`() {
        val request = ScheduleTegRequest(
            tasks = listOf()
        )

        restClient.post()
        .uri("/api/v1/teg/schedule")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Auth-Principal", IDENTITY_SUB)
            .body(request)
            .exchange()
            .expectStatus().isBadRequest
    }
}

class TegTaskDTOBuilder {
    var name: String? = "default-task"
    var implementationName: String? = "default-implementation"
    var inputs: List<TegArtefactDefinitionDTO>? = emptyList()
    var outputs: List<TegArtefactDefinitionDTO>? = emptyList()
    var arguments: List<String>? = emptyList()
    var timeout: DurationDTO? = DurationDTO(amount = 30, temporalUnit = TemporalUnitDTO.SECONDS)

    fun name(name: String?) = apply { this.name = name }
    fun implementationName(implementationName: String?) = apply { this.implementationName = implementationName }
    fun inputs(inputs: List<TegArtefactDefinitionDTO>?) = apply { this.inputs = inputs }
    fun outputs(outputs: List<TegArtefactDefinitionDTO>?) = apply { this.outputs = outputs }
    fun arguments(arguments: List<String>?) = apply { this.arguments = arguments }
    fun timeout(timeout: DurationDTO?) = apply { this.timeout = timeout }

    fun build() = TegTaskDTO(
        name = name,
        implementationName = implementationName,
        inputs = inputs,
        outputs = outputs,
        arguments = arguments,
        timeout = timeout,
    )
}

fun tegTaskDTO(block: TegTaskDTOBuilder.() -> Unit = {}): TegTaskDTO =
    TegTaskDTOBuilder().apply(block).build()


