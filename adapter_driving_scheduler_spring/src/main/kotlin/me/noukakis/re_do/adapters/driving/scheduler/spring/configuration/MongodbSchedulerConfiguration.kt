package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import me.noukakis.re_do.adapters.common.spring.mongodb.MongodbPersistenceAdapter
import me.noukakis.re_do.scheduler.port.PersistencePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Duration

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
}