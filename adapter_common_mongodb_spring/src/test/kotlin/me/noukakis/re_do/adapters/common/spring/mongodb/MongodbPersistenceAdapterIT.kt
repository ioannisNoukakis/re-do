package me.noukakis.re_do.adapters.common.spring.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver
import io.mongock.runner.springboot.MongockSpringboot
import me.noukakis.re_do.adapters.common.spring.mongodb.migrations._001TegEventInitializerChange
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbTEGEvent
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGArtefactType
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.TegEventFilter
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.collectionExists
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.getCollectionName
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier
import kotlin.time.Duration.Companion.minutes

const val MONGODB_DB_NAME = "test"

@Testcontainers
class MongodbPersistenceAdapterIT {

    private lateinit var mongoClient: MongoClient
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var sut: MongodbPersistenceAdapter

    @Container
    private val mongoDbContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0.31"))
        .withStartupTimeout(Duration.ofMinutes(5))

    @BeforeEach
    fun setup() {
        mongoClient = MongoClients.create(mongoDbContainer.replicaSetUrl)
        mongoTemplate = MongoTemplate(mongoClient, MONGODB_DB_NAME)
        sut = MongodbPersistenceAdapter(
            mongoTemplate,
            cursorBatchSizeForGetAllTegNotEvents = 500,
            tegEventLookbackDuration = Duration.ofDays(365),
        )
        runMigrations()
    }

    @AfterEach
    fun tearDown() {
        mongoClient.close()
    }

    @Nested
    inner class Migrations {

        @Test
        fun `running mongock creates the tegId index`() {
            assertTrue(mongoTemplate.collectionExists<MongodbTEGEvent>()) {
                "Collection doesn't exist. Existing collections: ${
                    mongoTemplate.db.listCollectionNames().into(mutableListOf())
                }"
            }

            val indexes = mongoTemplate
                .getCollection(mongoTemplate.getCollectionName<MongodbTEGEvent>())
                .listIndexes()
                .into(mutableListOf<Document>())

            assertTrue(indexes.any(::isTegIdIndexPresent)) {
                "tegId index not found. Existing indexes: ${indexes.joinToString("\n")}"
            }
        }

        @Test
        fun `running mongock creates a ttl index on the timestamp field with 30 days expiry`() {
            val indexes = mongoTemplate
                .getCollection(mongoTemplate.getCollectionName<MongodbTEGEvent>())
                .listIndexes()
                .into(mutableListOf<Document>())

            assertTrue(indexes.any(::isTtlIndexOnTimestampPresent)) {
                "TTL index on timestamp not found. Existing indexes: ${indexes.joinToString("\n")}"
            }
        }

        @Test
        fun `rollback drops the teg events collection`() {
            runMigrations()

            _001TegEventInitializerChange(mongoTemplate).rollback()

            assertFalse(mongoTemplate.collectionExists<MongodbTEGEvent>())
        }
    }

    @Nested
    inner class PersistenceAdapter {
        @Nested
        inner class SavingEvents {

            @Test
            fun `saveEvents persists every supported event subtype`() {
                val tegId = "teg-save-all"
                val expectedEvents = allPossibleEvents()

                sut.saveEvents(tegId, expectedEvents)

                assertEquals(
                    expectedEvents.sortedBy { it.javaClass.name },
                    mongoTemplate.findAll<MongodbTEGEvent>().map { it.toModel() }.sortedBy { it.javaClass.name }
                )
            }
        }

        @Nested
        inner class GettingEventsForTeg {

            @Test
            fun `getEventsForTeg returns empty list when the teg has no events`() {
                assertTrue(sut.getEventsForTeg("missing-teg", TegEventFilter.All).isEmpty())
            }

            @Test
            fun `getEventsForTeg returns all persisted events for the requested teg`() {
                val tegId = "teg-with-events"
                val otherTegId = "other-teg"
                val expectedEvents = allPossibleEvents()

                sut.saveEvents(tegId, expectedEvents)
                sut.saveEvents(otherTegId, listOf(logEvent(90)))

                val actualEvents = sut.getEventsForTeg(tegId, TegEventFilter.All)

                assertEquals(
                    expectedEvents.sortedBy { it.javaClass.name },
                    actualEvents.sortedBy { it.javaClass.name }
                )
            }

            @Test
            fun `getEventsForTeg with state filter excludes log and progress events`() {
                val tegId = "teg-state-events"
                val allEvents = allPossibleEvents()

                sut.saveEvents(tegId, allEvents)

                val actualEvents = sut.getEventsForTeg(tegId, TegEventFilter.StateEvent)
                val expectedEvents = allEvents.filterNot { it is TEGEvent.Log || it is TEGEvent.Progress }

                assertEquals(
                    expectedEvents.sortedBy { it.javaClass.name },
                    actualEvents.sortedBy { it.javaClass.name }
                )
            }
        }

        @Nested
        inner class GettingTegsThatDontHaveEvent {

            @Test
            fun `returns no teg ids when all tegs contain the requested event type`() {
                sut.saveEvents("teg-a", listOf(createdEvent(0), completedEvent(1)))
                sut.saveEvents("teg-b", listOf(scheduledEvent(2), completedEvent(3)))

                val result = sut.getTegsThatDontHaveEvents(listOf(TEGEvent.Completed::class)).toList()

                assertTrue(result.isEmpty())
            }

            @Test
            fun `returns one teg id when exactly one teg lacks the requested event type`() {
                sut.saveEvents("teg-a", listOf(createdEvent(0), completedEvent(1)))
                sut.saveEvents("teg-b", listOf(createdEvent(2), scheduledEvent(3)))

                val result = sut.getTegsThatDontHaveEvents(listOf(TEGEvent.Completed::class)).toList()

                assertEquals(
                    listOf("teg-b" to listOf(createdEvent(2), scheduledEvent(3))),
                    sortResult(result),
                )
            }

            @Test
            fun `returns many teg ids when many tegs lack the requested event type`() {
                sut.saveEvents("teg-a", listOf(createdEvent(0), completedEvent(1)))
                sut.saveEvents("teg-b", listOf(createdEvent(2), scheduledEvent(3)))
                sut.saveEvents("teg-c", listOf(submitterIdentityEvent(4), failedEvent(5)))
                sut.saveEvents("teg-d", listOf(createdEvent(6), completedEvent(7)))

                val result = sut.getTegsThatDontHaveEvents(listOf(TEGEvent.Completed::class)).toList()

                assertEquals(
                    listOf(
                        "teg-b" to listOf(createdEvent(2), scheduledEvent(3)),
                        "teg-c" to listOf(submitterIdentityEvent(4), failedEvent(5)),
                    ),
                    sortResult(result),
                )
            }

            private fun sortResult(result: List<Pair<String, List<TEGEvent>>>): List<Pair<String, List<TEGEvent>>> =
                result.map { r -> r.first to r.second.sortedBy { it.timestamp } }.sortedBy { it.first }
        }

        @Nested
        inner class GettingTegsThatDontHaveEventLookback {

            private val NOW = Instant.ofEpochMilli(0)
            private lateinit var lookbackSut: MongodbPersistenceAdapter

            @BeforeEach
            fun setupLookbackSut() {
                lookbackSut = MongodbPersistenceAdapter(
                    mongodbTemplate = mongoTemplate,
                    cursorBatchSizeForGetAllTegNotEvents = 500,
                    tegEventLookbackDuration = Duration.ofDays(30),
                    getNow = { NOW }
                )
            }

            @Test
            fun `does not consider tegs whose events fall outside the lookback window`() {
                val withinWindow = NOW.minus(Duration.ofDays(30))
                val outsideWindow = NOW.minus(Duration.ofDays(31))

                sut.saveEvents("recent-teg", listOf(TEGEvent.Created(task = sampleTask("t"), timestamp = withinWindow)))
                sut.saveEvents("old-teg", listOf(TEGEvent.Created(task = sampleTask("t"), timestamp = outsideWindow)))

                val result = lookbackSut.getTegsThatDontHaveEvents(listOf(TEGEvent.Completed::class)).toList()

                assertEquals(listOf("recent-teg"), result.map { it.first })
            }
        }
    }

    private fun runMigrations() {
        GenericApplicationContext().apply {
            registerBean(MongoTemplate::class.java, Supplier { mongoTemplate })
            refresh()
        }.use { springContext ->
            MongockSpringboot.builder()
                .setDriver(MongoSync4Driver.withDefaultLock(mongoClient, MONGODB_DB_NAME))
                .addMigrationScanPackage("me.noukakis.re_do.adapters.common.spring.mongodb.migrations")
                .setSpringContext(springContext)
                .setTransactional(false)
                .buildRunner()
                .execute()
        }
    }

    private fun isTegIdIndexPresent(indexDefinition: Document): Boolean {
        val keys = indexDefinition["key"] as? Document ?: return false
        return keys["tegId"] == 1 || keys["tegId"] == 1L
    }

    private fun isTtlIndexOnTimestampPresent(indexDefinition: Document): Boolean {
        val keys = indexDefinition["key"] as? Document ?: return false
        val hasTimestampKey = keys["timestamp"] == 1 || keys["timestamp"] == 1L
        val thirtyDaysInSeconds = 30L * 24 * 60 * 60
        val expireAfterSeconds = indexDefinition["expireAfterSeconds"]
        val hasCorrectExpiry = expireAfterSeconds == thirtyDaysInSeconds || expireAfterSeconds == thirtyDaysInSeconds.toInt()
        return hasTimestampKey && hasCorrectExpiry
    }

    private fun allPossibleEvents(): List<TEGEvent> = listOf(
        submitterIdentityEvent(0),
        createdEvent(1),
        scheduledEvent(2),
        completedEvent(3),
        noMoreTasksToScheduleEvent(4),
        tegFailedEvent(5),
        failedEvent(6),
        progressEvent(7),
        logEvent(8),
    )

    private fun submitterIdentityEvent(secondOffset: Long) = TEGEvent.SubmitterIdentity(
        identity = Identity(sub = "submitter-$secondOffset", roles = listOf("ROLE_USER", "ROLE_ADMIN")),
        timestamp = timestamp(secondOffset),
    )

    private fun createdEvent(secondOffset: Long) = TEGEvent.Created(
        task = sampleTask("task-$secondOffset"),
        timestamp = timestamp(secondOffset),
    )

    private fun scheduledEvent(secondOffset: Long) = TEGEvent.Scheduled(
        taskName = "task-$secondOffset",
        timestamp = timestamp(secondOffset),
    )

    private fun completedEvent(secondOffset: Long) = TEGEvent.Completed(
        taskName = "task-$secondOffset",
        timestamp = timestamp(secondOffset),
        outputArtefacts = listOf(
            TEGArtefact.TEGArtefactFile(
                name = "report-$secondOffset",
                ref = "/artefacts/report-$secondOffset.txt",
                storedWith = "S3",
            ),
            TEGArtefact.TEGArtefactStringValue(
                name = "summary-$secondOffset",
                value = "done-$secondOffset",
            ),
        ),
    )

    private fun noMoreTasksToScheduleEvent(secondOffset: Long) = TEGEvent.NoMoreTasksToSchedule(
        timestamp = timestamp(secondOffset),
    )

    private fun tegFailedEvent(secondOffset: Long) = TEGEvent.TEGFailed(
        timestamp = timestamp(secondOffset),
        reason = "teg-failed-$secondOffset",
    )

    private fun failedEvent(secondOffset: Long) = TEGEvent.Failed(
        taskName = "task-$secondOffset",
        timestamp = timestamp(secondOffset),
        reason = "failed-$secondOffset",
    )

    private fun progressEvent(secondOffset: Long) = TEGEvent.Progress(
        taskName = "task-$secondOffset",
        timestamp = timestamp(secondOffset),
        progress = (secondOffset * 10).toInt(),
    )

    private fun logEvent(secondOffset: Long) = TEGEvent.Log(
        taskName = "task-$secondOffset",
        timestamp = timestamp(secondOffset),
        log = "log-$secondOffset",
    )

    private fun sampleTask(name: String) = TEGTask(
        name = name,
        implementationName = "$name-impl",
        inputs = listOf(
            TEGArtefactDefinition(name = "input-file", type = TEGArtefactType.FILE),
            TEGArtefactDefinition(name = "input-config", type = TEGArtefactType.STRING_VALUE),
        ),
        outputs = listOf(
            TEGArtefactDefinition(name = "output-report", type = TEGArtefactType.FILE),
            TEGArtefactDefinition(name = "output-summary", type = TEGArtefactType.STRING_VALUE),
        ),
        arguments = listOf("--mode=test", "--task=$name"),
        timeout = 5.minutes,
    )

    private fun timestamp(secondOffset: Long): Instant = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(secondOffset)
}