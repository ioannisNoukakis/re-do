package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.adapters.driven.common.StubFileStorageAdapter
import me.noukakis.re_do.adapters.driven.common.StubUuidAdapter
import me.noukakis.re_do.adapters.driven.scheduler.adapter.FakeFileReferenceStoreAdapter
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.FileReference
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Path

const val TEST_UPLOAD_FILE_ID = "test-file-id"

class UploadFileUseCaseSutBuilder {
    val fileReferenceStoreAdapter = FakeFileReferenceStoreAdapter()
    val uuidAdapter = StubUuidAdapter(listOf(TEST_UPLOAD_FILE_ID))
    var result: UploadFileResult? = null

    fun whenUploadingFile(
        identity: Identity,
        sourcePath: Path,
    ) {
        val sut = UploadFileUseCase(
            StubFileStorageAdapter(),
            fileReferenceStoreAdapter,
            uuidAdapter,
        )
        result = sut.execute(UploadFileCommand(identity, sourcePath))
    }

    fun thenTheResultIs(expected: UploadFileResult) {
        assertEquals(expected, result)
    }

    fun thenThePersistedReferencesShouldBe(vararg expected: FileReference) {
        assertEquals(expected.toList(), fileReferenceStoreAdapter.savedReferences)
    }
}
