package me.noukakis.re_do.scheduler.service

import arrow.core.Tuple4
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.adapter.FakeFileReferenceStoreAdapter
import me.noukakis.re_do.scheduler.adapter.StubFileStorageAdapter
import me.noukakis.re_do.scheduler.adapter.StubUuidAdapter
import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.scheduler.port.StoredFileRef
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.ByteArrayInputStream
import java.io.InputStream

const val TEST_UPLOAD_FILE_ID = "test-file-id"

class UploadFileUseCaseSutBuilder {
    val fileReferenceStoreAdapter = FakeFileReferenceStoreAdapter()
    val uuidAdapter = StubUuidAdapter(TEST_UPLOAD_FILE_ID)
    private var storedFileRef: StoredFileRef = StoredFileRef(ref = "default/ref", storedWith = "default-backend")
    var result: UploadFileResult? = null

    fun givenStorageReturns(ref: StoredFileRef) {
        storedFileRef = ref
    }

    fun whenUploadingFile(
        identity: Identity,
        filename: String,
        contentType: String,
        contentLength: Long = 0L,
        stream: InputStream = ByteArrayInputStream(ByteArray(0)),
    ) {
        val sut = UploadFileUseCase(
            StubFileStorageAdapter(mutableMapOf(
                Tuple4(
                    TEST_UPLOAD_FILE_ID,
                    filename,
                    contentType,
                    contentLength
                ) to
                storedFileRef
            )),
            fileReferenceStoreAdapter,
            uuidAdapter,
        )
        result = sut.execute(UploadFileCommand(identity, filename, contentType, contentLength, stream))
    }

    fun thenTheResultIs(expected: UploadFileResult) {
        assertEquals(expected, result)
    }

    fun thenThePersistedReferencesShouldBe(vararg expected: FileReference) {
        assertEquals(expected.toList(), fileReferenceStoreAdapter.savedReferences)
    }
}
