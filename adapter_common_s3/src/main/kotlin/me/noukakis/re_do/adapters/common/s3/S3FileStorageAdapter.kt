package me.noukakis.re_do.adapters.common.s3

import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import org.apache.tika.Tika
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Path

class S3FileStorageAdapter(
    private val s3Client: S3Client,
    private val bucketName: String,
) : FileStoragePort {
    private val tika = Tika()

    override fun upload(ref: String, sourcePath: Path): StoredFileRef {
        val filename = sourcePath.fileName.toString()
        val contentType = tika.detect(sourcePath.toFile())
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(ref)
                .contentType(contentType)
                .contentDisposition("attachment; filename=\"$filename\"")
                .build(),
            RequestBody.fromFile(sourcePath.toFile())
        )
        return StoredFileRef(ref = ref, storedWith = "s3")
    }

    override fun download(ref: String, targetPath: Path): Path {
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(ref)
                .build(),
            ResponseTransformer.toFile(targetPath)
        )
        return targetPath
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
