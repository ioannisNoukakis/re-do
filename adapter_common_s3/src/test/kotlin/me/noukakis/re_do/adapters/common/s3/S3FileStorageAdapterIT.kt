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

private const val FILE_ID = "reports/file-abc-123"
private const val CONTENTS = "col1,col2\nval1,val2"

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
    private lateinit var testFile: Path

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        testFile = tempDir.resolve("report.csv")
        testFile.toFile().createNewFile()
        testFile.toFile().writeText(CONTENTS)
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
            val result = sut.upload(
                ref = FILE_ID,
                sourcePath = testFile,
            )

            assertEquals(StoredFileRef(ref = FILE_ID, storedWith = "s3"), result)
        }

        @Test
        fun `upload stores the file content at the expected key in the bucket`() {
            sut.upload(
                ref = FILE_ID,
                sourcePath = testFile,
            )

            val stored = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(TEST_BUCKET).key(FILE_ID).build()
            ).asUtf8String()
            assertEquals(CONTENTS, stored)
        }

        @Test
        fun `upload stores the correct content type`() {
            sut.upload(
                ref = FILE_ID,
                sourcePath = testFile,
            )

            val metadata = s3Client.headObject {
                it.bucket(TEST_BUCKET).key(FILE_ID)
            }
            assertEquals("text/csv", metadata.contentType())
        }
    }

    @Nested
    inner class Download {

        @Test
        fun `download returns the target path`(@TempDir tempDir: Path) {
            sut.upload(
                ref = FILE_ID,
                sourcePath = testFile,
            )

            val targetPath = tempDir.resolve("downloaded.csv")
            val result = sut.download(FILE_ID, targetPath)

            assertEquals(targetPath, result)
        }

        @Test
        fun `download writes the file content to the target path`(@TempDir tempDir: Path) {
            sut.upload(
                ref = FILE_ID,
                sourcePath = testFile,
            )

            val targetPath = tempDir.resolve("downloaded.csv")
            sut.download(FILE_ID, targetPath)

            assertEquals(CONTENTS, targetPath.toFile().readText())
        }
    }
}
