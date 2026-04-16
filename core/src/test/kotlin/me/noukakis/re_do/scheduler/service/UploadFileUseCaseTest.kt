package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.adapters.driven.common.STORED_WITH_STUB
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.FileReference
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

private val UPLOADER_IDENTITY = Identity(sub = "user-456", roles = listOf("file-uploader"))
private val SOURCE_PATH = Path.of("/tmp/report.csv")

class UploadFileUseCaseTest {
    private lateinit var sut: UploadFileUseCaseSutBuilder

    @BeforeEach
    fun setup() {
        sut = UploadFileUseCaseSutBuilder()
    }

    @Test
    fun `should return the file id and storage reference after a successful upload`() {
        sut.whenUploadingFile(
            identity = UPLOADER_IDENTITY,
            sourcePath = SOURCE_PATH,
        )

        sut.thenTheResultIs(
            UploadFileResult(
                ref = TEST_UPLOAD_FILE_ID,
                storedWith = STORED_WITH_STUB,
            )
        )
    }

    @Test
    fun `should persist the file reference with the uploader identity`() {
        sut.whenUploadingFile(
            identity = UPLOADER_IDENTITY,
            sourcePath = SOURCE_PATH,
        )

        sut.thenThePersistedReferencesShouldBe(
            FileReference(
                fileId = TEST_UPLOAD_FILE_ID,
                ref = TEST_UPLOAD_FILE_ID,
                storedWith = STORED_WITH_STUB,
                uploadedBy = UPLOADER_IDENTITY,
            )
        )
    }
}
