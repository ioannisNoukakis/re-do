package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import me.noukakis.re_do.adapters.driven.scheduler.InMemoryPersistenceAdapter
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGArtefactType
import me.noukakis.re_do.scheduler.model.TEGEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

private const val OWNER_SUB = "user-owner"
private const val INTRUDER_SUB = "user-intruder"
private const val TEG_ID = "test-teg-id"
private val OWNER_IDENTITY = Identity(sub = OWNER_SUB, roles = listOf("scheduler-user"))
private val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val T1: Instant = Instant.parse("2026-01-01T00:00:01Z")
private val T2: Instant = Instant.parse("2026-01-01T00:00:02Z")

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TegEventStreamControllerIT {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var persistenceAdapter: InMemoryPersistenceAdapter

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
    }

    @Test
    fun `should return 404 when no SubmitterIdentity exists for the tegId`() {
        val result = mockMvc.perform(
            get("/api/v1/teg/$TEG_ID/events")
                .header("X-Auth-Principal", OWNER_SUB)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andReturn()

        assert(result.response.status == 404) {
            "Expected 404, got ${result.response.status}: ${result.response.contentAsString}"
        }
    }

    @Test
    fun `should return 403 when the caller sub does not match the submitter`() {
        persistenceAdapter.state[TEG_ID] = listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))

        val result = mockMvc.perform(
            get("/api/v1/teg/$TEG_ID/events")
                .header("X-Auth-Principal", INTRUDER_SUB)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andReturn()

        assert(result.response.status == 403) {
            "Expected 403, got ${result.response.status}: ${result.response.contentAsString}"
        }
    }

    @Test
    fun `should replay all historical events when the last one is terminal`() {
        persistenceAdapter.state[TEG_ID] = listOf(
            TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0),
            TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
            TEGEvent.NoMoreTasksToSchedule(timestamp = T2),
        )

        val mvcResult = mockMvc.perform(
            get("/api/v1/teg/$TEG_ID/events")
                .header("X-Auth-Principal", OWNER_SUB)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        val response = mockMvc.perform(asyncDispatch(mvcResult)).andReturn().response
        val body = response.contentAsString

        assert(response.status == 200) { "Expected 200, got ${response.status}" }
        assert(body.contains("\"type\":\"SubmitterIdentity\"")) {
            "Expected SubmitterIdentity in body, was: $body"
        }
        assert(body.contains("\"type\":\"Scheduled\"") && body.contains("\"taskName\":\"task-a\"")) {
            "Expected Scheduled task-a in body, was: $body"
        }
        assert(body.contains("\"type\":\"NoMoreTasksToSchedule\"")) {
            "Expected NoMoreTasksToSchedule in body, was: $body"
        }
    }

    @Test
    fun `should stream newly-appended events through the SSE response`() {
        persistenceAdapter.state[TEG_ID] = listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))

        val mvcResult = mockMvc.perform(
            get("/api/v1/teg/$TEG_ID/events")
                .header("X-Auth-Principal", OWNER_SUB)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        persistenceAdapter.saveEvents(
            TEG_ID,
            listOf(
                TEGEvent.Created(
                    task = TEGTask(
                        name = "task-a",
                        implementationName = "implementationName",
                        inputs = listOf(
                            TEGArtefactDefinition(
                                name = "input-1",
                                type = TEGArtefactType.STRING_VALUE,
                            ),
                            TEGArtefactDefinition(
                                name = "input-2",
                                type = TEGArtefactType.FILE,
                            ),
                        ),
                        outputs = listOf(
                            TEGArtefactDefinition(
                                name = "output-1",
                                type = TEGArtefactType.STRING_VALUE,
                            ),
                            TEGArtefactDefinition(
                                name = "output-2",
                                type = TEGArtefactType.FILE,
                            ),
                        ),
                        arguments = listOf("input-1", "input-2"),
                        timeout = 5.minutes,
                    ),
                    timestamp = T0,
                ),
                TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
                TEGEvent.Progress(taskName = "task-a", step = "computing", progress = 55, timestamp = T1),
                TEGEvent.Log(taskName = "task-a", log = "some log message", timestamp = T1),
                TEGEvent.Completed(
                    taskName = "task-a",
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactFile(name = "output-2", ref = "temp-ref", storedWith = "some-storage"),
                        TEGArtefact.TEGArtefactStringValue(name = "output-3", value = "some-value"),
                    ),
                    timestamp = T1,
                ),
                TEGEvent.Failed(taskName = "task-a", reason = "some failure reason", timestamp = T2),
                TEGEvent.NoMoreTasksToSchedule(timestamp = T2),
            ),
        )

        val response = mockMvc.perform(asyncDispatch(mvcResult)).andReturn().response
        val body = response.contentAsString

        assert(response.status == 200) { "Expected 200, got ${response.status}" }
        assert(body.contains("\"type\":\"Created\"") && body.contains("\"taskName\":\"task-a\"")) {
            "Expected Created task-a in body, was: $body"
        }
        assert(body.contains("\"type\":\"Progress\"") && body.contains("\"step\":\"computing\"") && body.contains("\"progress\":55")) {
            "Expected Progress step=computing progress=55 in body, was: $body"
        }
        assert(body.contains("\"type\":\"Log\"") && body.contains("\"log\":\"some log message\"")) {
            "Expected Log some log message in body, was: $body"
        }
        assert(body.contains("\"type\":\"Completed\"") && body.contains("\"taskName\":\"task-a\"") && body.contains("\"name\":\"output-2\"") && body.contains("\"name\":\"output-3\"")) {
            "Expected Completed task-a with output-2 and output-3 in body, was: $body"
        }
        assert(body.contains("\"type\":\"Failed\"")) {
            "Expected Failed task-a in body, was: $body"
        }
        assert(body.contains("\"type\":\"NoMoreTasksToSchedule\"")) {
            "Expected NoMoreTasksToSchedule in body, was: $body"
        }
    }

    @Test
    fun `should stream newly-appended events through the SSE response - teg failed`() {
        persistenceAdapter.state[TEG_ID] = listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))

        val mvcResult = mockMvc.perform(
            get("/api/v1/teg/$TEG_ID/events")
                .header("X-Auth-Principal", OWNER_SUB)
                .accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        persistenceAdapter.saveEvents(
            TEG_ID,
            listOf(
                TEGEvent.TEGFailed(timestamp = T1, reason = "some failure reason"),
            ),
        )

        val response = mockMvc.perform(asyncDispatch(mvcResult)).andReturn().response
        val body = response.contentAsString

        assert(response.status == 200) { "Expected 200, got ${response.status}" }
        assert(body.contains("\"type\":\"TEGFailed\"") && body.contains("\"reason\":\"some failure reason\"")) {
            "Expected TEGFailed with reason some failure reason in body, was: $body"
        }
    }
}
