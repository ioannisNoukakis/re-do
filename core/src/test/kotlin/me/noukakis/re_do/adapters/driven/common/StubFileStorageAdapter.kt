package me.noukakis.re_do.adapters.driven.common

import arrow.core.Tuple4
import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import java.io.InputStream
import java.nio.file.Path

class StubFileStorageAdapter(
    val storage: MutableMap<Tuple4<String, String, String, Long>, StoredFileRef> = mutableMapOf(),
) : FileStoragePort {
    override fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef {
        val ref = StoredFileRef(ref = fileId, storedWith = "StubFileStorageAdapter")
        storage[Tuple4(fileId, filename, contentType, contentLength)] = ref
        return ref
    }

    override fun download(fileId: String, targetPath: Path): Path {
        for (entry in storage.entries) {
            if (entry.key.first == fileId) {
                return targetPath
            }
        }
        throw IllegalStateException("No stubbed file for fileId=$fileId")
    }
}