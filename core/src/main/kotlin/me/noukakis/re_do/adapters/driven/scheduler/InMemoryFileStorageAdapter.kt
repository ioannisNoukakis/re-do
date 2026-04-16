package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.writeBytes

class InMemoryFileStorageAdapter : FileStoragePort {
    private val storage = ConcurrentHashMap<String, ByteArray>()

    override fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef {
        storage[fileId] = stream.readAllBytes()
        return StoredFileRef(ref = fileId, storedWith = "InMemoryFileStorageAdapter")
    }

    override fun download(fileId: String, targetPath: Path): Path {
        val contents = storage[fileId] ?: throw IllegalStateException("No file with id $fileId in storage")
        targetPath.writeBytes(contents)
        return targetPath
    }
}
