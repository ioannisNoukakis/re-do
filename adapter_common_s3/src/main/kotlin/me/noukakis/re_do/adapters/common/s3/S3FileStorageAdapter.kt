package me.noukakis.re_do.adapters.common.s3

import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.StoredFileRef
import org.apache.tika.Tika
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.TransferListener
import java.net.URI
import java.nio.file.Path

class S3FileStorageAdapter(
    private val transferManager: S3TransferManager,
    private val bucketName: String,
) : FileStoragePort {
    private val tika = Tika()

    override fun upload(ref: String, sourcePath: Path, onProgress: (Int) -> Unit): StoredFileRef {
        val contentType = tika.detect(sourcePath.toFile())
        val filename = sourcePath.fileName.toString()
        transferManager.uploadFile(
            UploadFileRequest.builder()
                .putObjectRequest { req ->
                    req.bucket(bucketName)
                        .key(ref)
                        .contentType(contentType)
                        .contentDisposition("attachment; filename=\"$filename\"")
                }
                .source(sourcePath)
                .addTransferListener(progressListener(onProgress))
                .build()
        ).completionFuture().join()
        return StoredFileRef(ref = ref, storedWith = "s3")
    }

    override fun download(ref: String, targetPath: Path, onProgress: (Int) -> Unit): Path {
        transferManager.downloadFile(
            DownloadFileRequest.builder()
                .getObjectRequest { req -> req.bucket(bucketName).key(ref) }
                .destination(targetPath)
                .addTransferListener(progressListener(onProgress))
                .build()
        ).completionFuture().join()
        return targetPath
    }

    private fun progressListener(onProgress: (Int) -> Unit): TransferListener = object : TransferListener {
        private var lastReportedPercent = -1

        override fun bytesTransferred(context: TransferListener.Context.BytesTransferred) {
            val ratio = context.progressSnapshot().ratioTransferred()
            val percent = if (ratio.isPresent) (ratio.asDouble * 100).toInt().coerceIn(0, 100) else 0
            val snappedPercent = (percent / 10) * 10
            if (snappedPercent != lastReportedPercent) {
                lastReportedPercent = snappedPercent
                onProgress(snappedPercent)
            }
        }

        override fun transferComplete(context: TransferListener.Context.TransferComplete) {
            if (lastReportedPercent != 100) {
                lastReportedPercent = 100
                onProgress(100)
            }
        }
    }

    companion object {
        fun create(
            endpoint: String,
            bucket: String,
            accessKey: String,
            secretKey: String,
            region: String = "us-east-1",
        ): S3FileStorageAdapter {
            val s3AsyncClient = S3AsyncClient.builder()
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
                .multipartEnabled(true)
                .build()
            val transferManager = S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build()
            return S3FileStorageAdapter(transferManager, bucket)
        }
    }
}
