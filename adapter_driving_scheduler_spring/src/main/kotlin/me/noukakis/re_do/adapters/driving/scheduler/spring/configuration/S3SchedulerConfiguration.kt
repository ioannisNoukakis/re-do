package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import me.noukakis.re_do.adapters.common.s3.S3FileStorageAdapter
import me.noukakis.re_do.scheduler.port.FileStoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class S3SchedulerConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["scheduler.file-storage.mode"], havingValue = "s3")
    fun fileStoragePortBean(
        @Value("\${scheduler.file-storage.s3.endpoint}") endpoint: String,
        @Value("\${scheduler.file-storage.s3.bucket}") bucket: String,
        @Value("\${scheduler.file-storage.s3.access-key}") accessKey: String,
        @Value("\${scheduler.file-storage.s3.secret-key}") secretKey: String,
        @Value("\${scheduler.file-storage.s3.region:us-east-1}") region: String,
    ): FileStoragePort = S3FileStorageAdapter.create(endpoint, bucket, accessKey, secretKey, region)
}
