package me.noukakis.re_do.common.port

import java.nio.file.Path

data class StoredFileRef(val ref: String, val storedWith: String)

interface FileStoragePort {
    fun upload(ref: String, sourcePath: Path): StoredFileRef
    fun download(ref: String, targetPath: Path): Path
}
