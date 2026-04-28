package me.noukakis.re_do.common.port

import java.nio.file.Path

data class StoredFileRef(val ref: String, val storedWith: String)

interface FileStoragePort {
    fun upload(ref: String, sourcePath: Path, onProgress: (Int) -> Unit = {}): StoredFileRef
    fun download(ref: String, targetPath: Path, onProgress: (Int) -> Unit = {}): Path
}
