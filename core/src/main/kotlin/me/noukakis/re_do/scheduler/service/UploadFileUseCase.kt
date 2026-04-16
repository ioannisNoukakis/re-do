package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort
import me.noukakis.re_do.scheduler.port.FileStoragePort
import me.noukakis.re_do.scheduler.port.UUIDPort
import java.io.InputStream

data class UploadFileCommand(
    val identity: Identity,
    val filename: String,
    val contentType: String,
    val contentLength: Long,
    val stream: InputStream,
)

data class UploadFileResult(
    val fileId: String,
    val ref: String,
    val storedWith: String,
)

class UploadFileUseCase(
    private val fileStoragePort: FileStoragePort,
    private val fileReferenceStorePort: FileReferenceStorePort,
    private val uuidPort: UUIDPort,
) {
    fun execute(command: UploadFileCommand): UploadFileResult {
        val fileId = uuidPort.generateUUID()
        val storageRef = fileStoragePort.upload(fileId, command.filename, command.contentType, command.contentLength, command.stream)
        fileReferenceStorePort.save(
            FileReference(
                fileId = fileId,
                ref = storageRef.ref,
                storedWith = storageRef.storedWith,
                uploadedBy = command.identity,
            )
        )
        return UploadFileResult(
            fileId = fileId,
            ref = storageRef.ref,
            storedWith = storageRef.storedWith,
        )
    }
}
