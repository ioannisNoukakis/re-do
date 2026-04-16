package me.noukakis.re_do.adapters.common.spring.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import me.noukakis.re_do.adapters.common.spring.mongodb.migrations._002FileReferenceInitializerChange
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbFileReference
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.FileReference
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.collectionExists
import org.springframework.data.mongodb.core.findAll
import org.springframework.data.mongodb.core.getCollectionName
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Testcontainers
class MongodbFileReferenceStoreAdapterIT {

    private lateinit var mongoClient: MongoClient
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var sut: MongodbFileReferenceStoreAdapter

    @Container
    private val mongoDbContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0.31"))
        .withStartupTimeout(Duration.ofMinutes(5))

    @BeforeEach
    fun setup() {
        mongoClient = MongoClients.create(mongoDbContainer.replicaSetUrl)
        mongoTemplate = MongoTemplate(mongoClient, MONGODB_DB_NAME)
        sut = MongodbFileReferenceStoreAdapter(mongoTemplate)
        runMigrations(mongoClient, mongoTemplate)
    }

    @AfterEach
    fun tearDown() {
        mongoClient.close()
    }

    @Nested
    inner class Migrations {

        @Test
        fun `running mongock creates the file_references collection`() {
            assertTrue(mongoTemplate.collectionExists<MongodbFileReference>()) {
                "Collection doesn't exist. Existing collections: ${
                    mongoTemplate.db.listCollectionNames().into(mutableListOf())
                }"
            }
        }

        @Test
        fun `running mongock creates a unique index on the fileId field`() {
            val indexes = mongoTemplate
                .getCollection(mongoTemplate.getCollectionName<MongodbFileReference>())
                .listIndexes()
                .into(mutableListOf<Document>())

            assertTrue(indexes.any(::isUniqueFileIdIndexPresent)) {
                "Unique fileId index not found. Existing indexes: ${indexes.joinToString("\n")}"
            }
        }

        @Test
        fun `rollback drops the file_references collection`() {
            _002FileReferenceInitializerChange(mongoTemplate).rollback()

            assertFalse(mongoTemplate.collectionExists<MongodbFileReference>())
        }
    }

    @Nested
    inner class FileReferenceStoreAdapter {

        @Test
        fun `save persists all fields of the file reference`() {
            val reference = FileReference(
                fileId = "file-abc-123",
                ref = "bucket/uploads/file-abc-123",
                storedWith = "s3",
                uploadedBy = Identity(sub = "user-456", roles = listOf("uploader", "admin")),
            )

            sut.save(reference)

            val saved = mongoTemplate.findAll<MongodbFileReference>()
            assertEquals(listOf(reference), saved.map { it.toModel() })
        }
    }

    private fun isUniqueFileIdIndexPresent(indexDefinition: Document): Boolean {
        val keys = indexDefinition["key"] as? Document ?: return false
        val hasFileIdKey = keys[MongodbFileReference::fileId.name] == 1 || keys[MongodbFileReference::fileId.name] == 1L
        val isUnique = indexDefinition["unique"] == true
        return hasFileIdKey && isUnique
    }
}
