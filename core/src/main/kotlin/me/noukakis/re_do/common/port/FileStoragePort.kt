package me.noukakis.re_do.common.port

import java.io.InputStream
import java.nio.file.Path

data class StoredFileRef(val ref: String, val storedWith: String)

interface FileStoragePort {
    fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef
    fun download(fileId: String, targetPath: Path): Path
}
