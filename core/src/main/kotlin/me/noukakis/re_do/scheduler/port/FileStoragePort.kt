package me.noukakis.re_do.scheduler.port

import java.io.InputStream

data class StoredFileRef(val ref: String, val storedWith: String)

interface FileStoragePort {
    fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef
}
