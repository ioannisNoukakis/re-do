package me.noukakis.re_do.adapters.common.spring.mongodb

import MONGODB_IMAGE
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import me.noukakis.re_do.adapters.common.spring.mongodb.migrations._003MutualExclusionLockChange
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbMutualExclusionLock
import me.noukakis.re_do.scheduler.port.LockTimeoutException
import org.bson.Document
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.collectionExists
import org.springframework.data.mongodb.core.getCollectionName
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

private const val TEG_ID = "test-teg-id"

@Testcontainers
class MongoMutualExclusionLockAdapterIT {
    private lateinit var mongoClient: MongoClient
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var sut: MongoMutualExclusionLockAdapter

    private val NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    @Container
    private val mongoDbContainer = MongoDBContainer(DockerImageName.parse(MONGODB_IMAGE))
        .withStartupTimeout(Duration.ofMinutes(5))


    @BeforeEach
    fun setup() {
        mongoClient = MongoClients.create(mongoDbContainer.replicaSetUrl)
        mongoTemplate = MongoTemplate(mongoClient, MONGODB_DB_NAME)
        sut = MongoMutualExclusionLockAdapter(mongoTemplate, getNow = { NOW })
        runMigrations(mongoClient, mongoTemplate)
    }

    @Nested
    inner class Migrations {
        @Test
        fun `running mongock creates the ttl index`() {
            assertTrue(mongoTemplate.collectionExists<MongodbMutualExclusionLock>()) {
                "Collection doesn't exist. Existing collections: ${
                    mongoTemplate.db.listCollectionNames().into(mutableListOf())
                }"
            }

            val indexes = mongoTemplate
                .getCollection(mongoTemplate.getCollectionName<MongodbMutualExclusionLock>())
                .listIndexes()
                .into(mutableListOf<Document>())

            assertTrue(indexes.any(::isTTLIndexPresent)) {
                "ttl index not found. Existing indexes: ${indexes.joinToString("\n")}"
            }
        }

        @Test
        fun `rollback drops the mutual_exclusion_lock events collection`() {
            runMigrations(mongoClient, mongoTemplate)

            _003MutualExclusionLockChange(mongoTemplate).rollback()

            assertFalse(mongoTemplate.collectionExists<MongodbMutualExclusionLock>())
        }
    }

    @Nested
    inner class `Mutual exclusion lock adapter` {
        @BeforeEach
        fun setup() {
            runMigrations(mongoClient, mongoTemplate)
        }

        @Test
        fun `acquireLock sets a lock entry in the collection`() {
            sut.lock(TEG_ID)

            assertEquals(
                listOf(
                    MongodbMutualExclusionLock(
                        id = TEG_ID,
                        acquiredAt = NOW
                    )
                ),
                mongoTemplate.findAll(MongodbMutualExclusionLock::class.java)
            )
        }

        @Test
        fun `release lock removes the lock entry in the collection`() {
            sut.lock(TEG_ID)
            sut.release(TEG_ID)

            assertEquals(
                emptyList(),
                mongoTemplate.findAll(MongodbMutualExclusionLock::class.java)
            )
        }

        @Test
        fun `unlocking a non-existing lock is a no-op`() {
            sut.release(TEG_ID)
        }

        @Test
        fun `acquiring a lock blocks the current thread until the lock is released`() {
            val countDownLatchBefore = CountDownLatch(1)
            val countDownLatchAfter = CountDownLatch(1)
            sut.lock(TEG_ID)

            val thread = Thread {
                countDownLatchBefore.countDown()
                sut.lock(TEG_ID)
                countDownLatchAfter.countDown()
            }
            thread.start()
            countDownLatchBefore.await()

            assertFalse(countDownLatchAfter.await(1, TimeUnit.SECONDS)) { "Expected lock to still be held" }
            sut.release(TEG_ID)
            sut.lock(TEG_ID)
        }

        @Test
        @Timeout(1, unit = TimeUnit.SECONDS)
        fun `acquiring a lock throws LockTimeoutException after the configured timeout`() {
            val sutWithShortTimeout = MongoMutualExclusionLockAdapter(
                mongoTemplate,
                getNow = Instant::now,
                retryInterval = Duration.ofMillis(50),
                lockTimeout = Duration.ofMillis(300),
            )
            sutWithShortTimeout.lock(TEG_ID)

            assertThrows<LockTimeoutException> {
                sutWithShortTimeout.lock(TEG_ID)
            }
        }

        @Test
        fun `only one thread may acquire the lock at the same time`() {
            val countDownLatchAllStarted = CountDownLatch(100)
            val countDownLatchAtLeastOneEntered = CountDownLatch(1)
            val threads = List(100) {
                Thread {
                    countDownLatchAllStarted.countDown()
                    sut.lock(TEG_ID)
                    countDownLatchAtLeastOneEntered.countDown()
                }
            }
            threads.forEach { it.start() }
            countDownLatchAllStarted.await()
            countDownLatchAtLeastOneEntered.await()

            val locks = mongoTemplate.findAll(MongodbMutualExclusionLock::class.java)
            assertEquals(1, locks.size)
        }
    }

    private fun isTTLIndexPresent(indexDefinition: Document): Boolean {
        val keys = indexDefinition["key"] as? Document ?: return false
        return keys["acquiredAt"] == 1 || keys["acquiredAt"] == 1L
    }
}