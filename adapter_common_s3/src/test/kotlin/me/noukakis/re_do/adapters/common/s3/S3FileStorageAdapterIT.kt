package me.noukakis.re_do.adapters.common.s3

import me.noukakis.re_do.common.port.StoredFileRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.net.URI
import java.nio.file.Path
import java.time.Duration

private const val RUSTFS_PORT = 9000
private const val ACCESS_KEY = "testadmin"
private const val SECRET_KEY = "testadmin123"
private const val TEST_BUCKET = "test-bucket"

@Testcontainers
class S3FileStorageAdapterIT {

    @Container
    private val rustfsContainer = GenericContainer(DockerImageName.parse("rustfs/rustfs:1.0.0-alpha.90"))
        .withExposedPorts(RUSTFS_PORT)
        .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
        .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
        .withStartupTimeout(Duration.ofMinutes(2))

    private lateinit var s3Client: S3Client
    private lateinit var sut: S3FileStorageAdapter

    @BeforeEach
    fun setup() {
        val endpoint = "http://${rustfsContainer.host}:${rustfsContainer.getMappedPort(RUSTFS_PORT)}"
        s3Client = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
                )
            )
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()

        s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build())
        sut = S3FileStorageAdapter(s3Client, TEST_BUCKET)
    }

    @Nested
    inner class Upload {

        @Test
        fun `upload returns the file id as ref and s3 as storedWith`() {
            val content = "col1,col2\nval1,val2"
            val result = sut.upload(
                fileId = "reports/file-abc-123",
                filename = "report.csv",
                contentType = "text/csv",
                contentLength = content.length.toLong(),
                stream = content.byteInputStream(),
            )

            assertEquals(StoredFileRef(ref = "reports/file-abc-123", storedWith = "s3"), result)
        }

        @Test
        fun `upload stores the file content at the expected key in the bucket`() {
            val content = "col1,col2\nval1,val2"

            sut.upload(
                fileId = "reports/file-abc-123",
                filename = "report.csv",
                contentType = "text/csv",
                contentLength = content.length.toLong(),
                stream = content.byteInputStream(),
            )

            val stored = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(TEST_BUCKET).key("reports/file-abc-123").build()
            ).asUtf8String()
            assertEquals(content, stored)
        }

        @Test
        fun `upload stores the correct content type`() {
            val content = "pdf-content"
            sut.upload(
                fileId = "docs/file-xyz-456",
                filename = "document.pdf",
                contentType = "application/pdf",
                contentLength = content.length.toLong(),
                stream = content.byteInputStream(),
            )

            val metadata = s3Client.headObject {
                it.bucket(TEST_BUCKET).key("docs/file-xyz-456")
            }
            assertEquals("application/pdf", metadata.contentType())
        }
    }

    @Nested
    inner class Download {

        @Test
        fun `download returns the target path`(@TempDir tempDir: Path) {
            val content = "col1,col2\nval1,val2"
            sut.upload(
                fileId = "reports/file-abc-123",
                filename = "report.csv",
                contentType = "text/csv",
                contentLength = content.length.toLong(),
                stream = content.byteInputStream(),
            )

            val targetPath = tempDir.resolve("downloaded.csv")
            val result = sut.download("reports/file-abc-123", targetPath)

            assertEquals(targetPath, result)
        }

        @Test
        fun `download writes the file content to the target path`(@TempDir tempDir: Path) {
            val content = "col1,col2\nval1,val2"
            sut.upload(
                fileId = "reports/file-abc-123",
                filename = "report.csv",
                contentType = "text/csv",
                contentLength = content.length.toLong(),
                stream = content.byteInputStream(),
            )

            val targetPath = tempDir.resolve("downloaded.csv")
            sut.download("reports/file-abc-123", targetPath)

            assertEquals(content, targetPath.toFile().readText())
        }
    }
}
