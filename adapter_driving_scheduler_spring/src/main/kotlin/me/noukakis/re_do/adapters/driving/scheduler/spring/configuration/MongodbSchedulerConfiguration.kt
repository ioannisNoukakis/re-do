package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import me.noukakis.re_do.adapters.common.spring.mongodb.MongoMutualExclusionLockAdapter
import me.noukakis.re_do.adapters.common.spring.mongodb.MongodbFileReferenceStoreAdapter
import me.noukakis.re_do.adapters.common.spring.mongodb.MongodbPersistenceAdapter
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort
import me.noukakis.re_do.scheduler.port.PersistencePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Duration
import java.time.Instant

@Configuration
class MongodbSchedulerConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["scheduler.persistence.mode"], havingValue = "mongodb")
    fun persistencePortBean(
        mongodbTemplate: MongoTemplate,
        @Value("\${scheduler.mongodb.cursor-batch-size-for-get-all-teg-not-events:100}") cursorBatchSizeForGetAllTegNotEvents: Int,
        @Value("\${scheduler.mongodb.teg-event-lookback-duration:30d}") tegEventLookbackDuration: Duration,
    ): PersistencePort {
        return MongodbPersistenceAdapter(
            mongodbTemplate = mongodbTemplate,
            cursorBatchSizeForGetAllTegNotEvents = cursorBatchSizeForGetAllTegNotEvents,
            tegEventLookbackDuration = tegEventLookbackDuration,
        )
    }

    @Bean
    @ConditionalOnProperty(name = ["scheduler.file-reference-store.mode"], havingValue = "mongodb")
    fun fileReferenceStorePortBean(
        mongodbTemplate: MongoTemplate,
    ): FileReferenceStorePort = MongodbFileReferenceStoreAdapter(mongodbTemplate)

    @Bean
    @ConditionalOnProperty("scheduler.mutual-exclusion-lock.mode", havingValue = "mongodb")
    fun mutualExclusionLockPortBean(
        mongodbTemplate: MongoTemplate,
        @Value("\${scheduler.mutual-exclusion-lock.retry-interval:100ms}") lockRetryInterval: Duration,
        @Value("\${scheduler.mutual-exclusion-lock.timeout:30s}") lockTimeout: Duration,
    ) = MongoMutualExclusionLockAdapter(
        mongoTemplate = mongodbTemplate,
        getNow = { Instant.now() },
        retryInterval = lockRetryInterval,
        lockTimeout = lockTimeout,
    )
}