package me.noukakis.re_do.adapters.common.spring.mongodb

import MONGODB_IMAGE
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.TegEventListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TEG_ID = "test-teg-id"
private val OWNER = Identity(sub = "user-owner", roles = listOf("scheduler-user"))
private val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val T1: Instant = Instant.parse("2026-01-01T00:00:01Z")
private val T2: Instant = Instant.parse("2026-01-01T00:00:02Z")

@Testcontainers
class MongodbTegEventStreamAdapterIT {

    private lateinit var mongoClient: MongoClient
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var container: DefaultMessageListenerContainer
    private lateinit var persistence: MongodbPersistenceAdapter
    private lateinit var sut: MongodbTegEventStreamAdapter

    @Container
    private val mongoDbContainer = MongoDBContainer(DockerImageName.parse(MONGODB_IMAGE))
        .withReplicaSet()
        .withStartupTimeout(Duration.ofMinutes(5))

    @BeforeEach
    fun setup() {
        mongoClient = MongoClients.create("${mongoDbContainer.replicaSetUrl}?directConnection=true")
        mongoTemplate = MongoTemplate(mongoClient, MONGODB_DB_NAME)
        runMigrations(mongoClient, mongoTemplate)
        container = DefaultMessageListenerContainer(mongoTemplate)
        container.start()
        persistence = MongodbPersistenceAdapter(
            mongoTemplate,
            cursorBatchSizeForGetAllTegNotEvents = 500,
            tegEventLookbackDuration = Duration.ofDays(365),
        )
        sut = MongodbTegEventStreamAdapter(mongoTemplate, container)
    }

    @AfterEach
    fun tearDown() {
        container.stop()
        mongoClient.close()
    }

    @Test
    fun `replays all events already present at subscription time`() {
        persistence.saveEvents(
            TEG_ID,
            listOf(
                TEGEvent.SubmitterIdentity(OWNER, T0),
                TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
            ),
        )

        val listener = RecordingListener()
        sut.stream(TEG_ID, listener).use {
            // synchronous replay; no waiting needed
        }

        assertEquals(
            listOf(
                TEGEvent.SubmitterIdentity(OWNER, T0),
                TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
            ),
            listener.events,
        )
    }

    @Test
    fun `completes the listener when the last event in history is terminal`() {
        persistence.saveEvents(
            TEG_ID,
            listOf(
                TEGEvent.SubmitterIdentity(OWNER, T0),
                TEGEvent.NoMoreTasksToSchedule(T2),
            ),
        )

        val listener = RecordingListener()
        sut.stream(TEG_ID, listener).use {}

        assertTrue(listener.completed.get()) { "Expected listener.onComplete to have been called" }
    }

    @Test
    fun `delivers newly-appended events to the listener through the change stream`() {
        persistence.saveEvents(TEG_ID, listOf(TEGEvent.SubmitterIdentity(OWNER, T0)))

        val listener = RecordingListener(awaitFor = 3)
        sut.stream(TEG_ID, listener).use {
            persistence.saveEvents(
                TEG_ID,
                listOf(
                    TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
                    TEGEvent.NoMoreTasksToSchedule(T2),
                ),
            )

            assertTrue(listener.latch.await(10, TimeUnit.SECONDS)) {
                "Timed out waiting for appended events; got: ${listener.events}"
            }
        }

        assertEquals(
            listOf(
                TEGEvent.SubmitterIdentity(OWNER, T0),
                TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
                TEGEvent.NoMoreTasksToSchedule(T2),
            ),
            listener.events,
        )
    }

    @Test
    fun `does not deliver events for other tegs`() {
        val otherTegId = UUID.randomUUID().toString()
        persistence.saveEvents(TEG_ID, listOf(TEGEvent.SubmitterIdentity(OWNER, T0)))

        val listener = RecordingListener(awaitFor = 2)
        sut.stream(TEG_ID, listener).use {
            persistence.saveEvents(otherTegId, listOf(TEGEvent.SubmitterIdentity(OWNER, T0)))
            persistence.saveEvents(TEG_ID, listOf(TEGEvent.NoMoreTasksToSchedule(T2)))
            assertTrue(listener.latch.await(10, TimeUnit.SECONDS))
        }

        assertEquals(
            listOf(
                TEGEvent.SubmitterIdentity(OWNER, T0),
                TEGEvent.NoMoreTasksToSchedule(T2),
            ),
            listener.events,
        )
    }

    private class RecordingListener(awaitFor: Int = 0) : TegEventListener {
        val events: MutableList<TEGEvent> = java.util.Collections.synchronizedList(mutableListOf())
        val completed = AtomicBoolean(false)
        val errors: MutableList<Throwable> = java.util.Collections.synchronizedList(mutableListOf())
        val latch = CountDownLatch(awaitFor)

        override fun onEvent(event: TEGEvent) {
            events.add(event)
            latch.countDown()
        }
        override fun onComplete() {
            completed.set(true)
        }
        override fun onError(t: Throwable) {
            errors.add(t)
        }
    }
}
