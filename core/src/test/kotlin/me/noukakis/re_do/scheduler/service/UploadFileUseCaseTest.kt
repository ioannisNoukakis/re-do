package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.common.port.StoredFileRef
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val UPLOADER_IDENTITY = Identity(sub = "user-456", roles = listOf("file-uploader"))

class UploadFileUseCaseTest {
    private lateinit var sut: UploadFileUseCaseSutBuilder

    @BeforeEach
    fun setup() {
        sut = UploadFileUseCaseSutBuilder()
    }

    @Test
    fun `should return the file id and storage reference after a successful upload`() {
        sut.givenStorageReturns(StoredFileRef(ref = "bucket/uploads/test-file-id", storedWith = "s3"))

        sut.whenUploadingFile(
            identity = UPLOADER_IDENTITY,
            filename = "report.csv",
            contentType = "text/csv",
        )

        sut.thenTheResultIs(
            UploadFileResult(
                fileId = TEST_UPLOAD_FILE_ID,
                ref = "bucket/uploads/test-file-id",
                storedWith = "s3",
            )
        )
    }

    @Test
    fun `should persist the file reference with the uploader identity`() {
        sut.givenStorageReturns(StoredFileRef(ref = "bucket/uploads/test-file-id", storedWith = "s3"))

        sut.whenUploadingFile(
            identity = UPLOADER_IDENTITY,
            filename = "report.csv",
            contentType = "text/csv",
        )

        sut.thenThePersistedReferencesShouldBe(
            FileReference(
                fileId = TEST_UPLOAD_FILE_ID,
                ref = "bucket/uploads/test-file-id",
                storedWith = "s3",
                uploadedBy = UPLOADER_IDENTITY,
            )
        )
    }
}
