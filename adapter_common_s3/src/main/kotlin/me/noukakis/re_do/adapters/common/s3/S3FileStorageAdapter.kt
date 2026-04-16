package me.noukakis.re_do.adapters.common.s3

import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream
import java.net.URI
import java.nio.file.Path

class S3FileStorageAdapter(
    private val s3Client: S3Client,
    private val bucketName: String,
) : FileStoragePort {

    override fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileId)
                .contentType(contentType)
                .contentDisposition("attachment; filename=\"$filename\"")
                .build(),
            RequestBody.fromInputStream(stream, contentLength)
        )
        return StoredFileRef(ref = fileId, storedWith = "s3")
    }

    override fun download(fileId: String, targetPath: Path): Path {
        TODO("Not yet implemented")
    }

    companion object {
        fun create(
            endpoint: String,
            bucket: String,
            accessKey: String,
            secretKey: String,
            region: String = "us-east-1",
        ): S3FileStorageAdapter {
            val s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                    )
                )
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build()
                )
                .build()
            return S3FileStorageAdapter(s3Client, bucket)
        }
    }
}
