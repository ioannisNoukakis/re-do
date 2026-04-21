package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import me.noukakis.re_do.adapters.driving.scheduler.spring.dto.UploadFileResponse
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.service.UploadFileCommand
import me.noukakis.re_do.scheduler.service.UploadFileUseCase
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files

@RestController
@RequestMapping("/api/v1/files")
class FileUploadController(
    private val uploadFileUseCase: UploadFileUseCase,
) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadFile(
        @RequestHeader("X-Auth-Principal") sub: String,
        @RequestHeader("X-Auth-Roles") roles: List<String>,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<UploadFileResponse> {
        var tmpFile: File? = null
        try {
            tmpFile = Files.createTempFile("upload-", null).toFile()
            file.transferTo(tmpFile)
            val result = uploadFileUseCase.execute(
                UploadFileCommand(
                    identity = Identity(sub, roles),
                    sourcePath = tmpFile.toPath()
                )
            )
            return ResponseEntity.ok(
                UploadFileResponse(
                    ref = result.ref,
                    storedWith = result.storedWith,
                )
            )
        } finally {
            tmpFile?.delete()
        }
    }
}
