package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.writeBytes

class InMemoryFileStorageAdapter : FileStoragePort {
    private val storage = ConcurrentHashMap<String, ByteArray>()

    override fun upload(ref: String, sourcePath: Path): StoredFileRef {
        storage[ref] = sourcePath.toFile().readBytes()
        return StoredFileRef(ref = ref, storedWith = "InMemoryFileStorageAdapter")
    }

    override fun download(ref: String, targetPath: Path): Path {
        val contents = storage[ref] ?: throw IllegalStateException("No file with id $ref in storage")
        targetPath.writeBytes(contents)
        return targetPath
    }
}
