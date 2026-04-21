package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.UUIDPort
import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort
import java.nio.file.Path

data class UploadFileCommand(
    val identity: Identity,
    val sourcePath: Path,
)

data class UploadFileResult(
    val ref: String,
    val storedWith: String,
)

class UploadFileUseCase(
    private val fileStoragePort: FileStoragePort,
    private val fileReferenceStorePort: FileReferenceStorePort,
    private val uuidPort: UUIDPort,
) {
    fun execute(command: UploadFileCommand): UploadFileResult {
        val fileId = uuidPort.next()
        val storageRef = fileStoragePort.upload(fileId, command.sourcePath)
        fileReferenceStorePort.save(
            FileReference(
                fileId = fileId,
                ref = storageRef.ref,
                storedWith = storageRef.storedWith,
                uploadedBy = command.identity,
            )
        )
        return UploadFileResult(
            ref = storageRef.ref,
            storedWith = storageRef.storedWith,
        )
    }
}
