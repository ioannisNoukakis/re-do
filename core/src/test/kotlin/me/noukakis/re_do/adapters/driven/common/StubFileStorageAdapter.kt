package me.noukakis.re_do.adapters.driven.common

import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import java.nio.file.Path

const val STORED_WITH_STUB = "StubFileStorageAdapter"

class StubFileStorageAdapter(
    val storage: MutableMap<String, StoredFileRef> = mutableMapOf(),
) : FileStoragePort {
    override fun upload(ref: String, sourcePath: Path): StoredFileRef {
        val storedRef = StoredFileRef(ref = ref, storedWith = STORED_WITH_STUB)
        storage[ref] = storedRef
        return storedRef
    }

    override fun download(ref: String, targetPath: Path): Path {
        for (entry in storage.entries) {
            if (entry.key == ref) {
                return targetPath
            }
        }
        throw IllegalStateException("No stubbed file for fileId=$ref")
    }
}