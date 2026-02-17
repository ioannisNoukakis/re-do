package me.noukakis.re_do.web.adapter_primary_spring.configuration

import me.noukakis.re_do.adapters.scheduler.InMemoryMessagingAdapter
import me.noukakis.re_do.adapters.scheduler.InMemoryPersistenceAdapter
import me.noukakis.re_do.adapters.scheduler.StdLibNowAdapter
import me.noukakis.re_do.adapters.scheduler.StdLibUuidAdapter
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.NowPort
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.UUIDPort
import me.noukakis.re_do.scheduler.service.TEGScheduler
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SchedulerConfiguration {
    @Bean
    @ConditionalOnProperty(name = ["app.service.scheduler.messaging.mode"], havingValue = "in-memory")
    fun messagingPortBean(): MessagingPort {
        return InMemoryMessagingAdapter()
    }

    @Bean
    @ConditionalOnProperty(name = ["app.service.scheduler.persistence.mode"], havingValue = "in-memory")
    fun persistencePortBean(): PersistencePort {
        return InMemoryPersistenceAdapter()
    }

    @Bean
    fun nowPort(): NowPort {
        return StdLibNowAdapter()
    }

    @Bean
    fun uuidPort(): UUIDPort {
        return StdLibUuidAdapter()
    }

    @Bean
    fun schedulerBean(
        messagingPort: MessagingPort,
        persistencePort: PersistencePort,
        uuidPort: UUIDPort,
        nowPort: NowPort,
        @Value("\${app.service.scheduler.max-failures-before-giving-up}") maxFailuresBeforeGivingUp: Int
    ): TEGScheduler {
        return TEGScheduler(
            messagingPort,
            persistencePort,
            uuidPort,
            nowPort,
            maxFailuresBeforeGivingUp
        )
    }
}