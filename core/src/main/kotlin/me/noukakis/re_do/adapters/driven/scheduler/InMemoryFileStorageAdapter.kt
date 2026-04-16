package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.port.FileStoragePort
import me.noukakis.re_do.scheduler.port.StoredFileRef
import java.io.InputStream

class InMemoryFileStorageAdapter : FileStoragePort {
    override fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef =
        StoredFileRef(ref = fileId, storedWith = "in-memory")
}
