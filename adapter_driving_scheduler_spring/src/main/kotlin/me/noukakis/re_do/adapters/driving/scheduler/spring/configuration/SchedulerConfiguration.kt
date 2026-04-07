package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import me.noukakis.re_do.adapters.common.spring.Slf4jLogAdapter
import me.noukakis.re_do.adapters.driven.common.InMemoryMessagingAdapter
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryPersistenceAdapter
import me.noukakis.re_do.adapters.driven.scheduler.StdLibNowAdapter
import me.noukakis.re_do.adapters.driven.scheduler.StdLibUuidAdapter
import me.noukakis.re_do.scheduler.port.LogPort
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
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "in-memory")
    fun messagingPortInMemoryBean(): MessagingPort {
        return InMemoryMessagingAdapter()
    }

    @Bean
    @ConditionalOnProperty(name = ["scheduler.persistence.mode"], havingValue = "in-memory")
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
    fun logPort(): LogPort = Slf4jLogAdapter()

    @Bean
    fun schedulerBean(
        messagingPort: MessagingPort,
        persistencePort: PersistencePort,
        uuidPort: UUIDPort,
        nowPort: NowPort,
        logPort: LogPort,
        @Value("\${scheduler.max-failures-before-giving-up}") maxFailuresBeforeGivingUp: Int
    ): TEGScheduler {
        return TEGScheduler(
            messagingPort,
            persistencePort,
            uuidPort,
            nowPort,
            maxFailuresBeforeGivingUp,
            logPort,
        )
    }
}