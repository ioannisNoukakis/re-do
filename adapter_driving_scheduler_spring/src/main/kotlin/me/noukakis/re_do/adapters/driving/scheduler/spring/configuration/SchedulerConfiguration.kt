package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import me.noukakis.re_do.adapters.common.spring.rabbitmq.Slf4jLogAdapter
import me.noukakis.re_do.adapters.driven.common.InMemoryMessagingAdapter
import me.noukakis.re_do.adapters.driven.common.StdLibUuidAdapter
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryFileReferenceStoreAdapter
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryFileStorageAdapter
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryMutualExclusionLockAdapter
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryPersistenceAdapter
import me.noukakis.re_do.adapters.driven.scheduler.StdLibNowAdapter
import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.UUIDPort
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort
import me.noukakis.re_do.scheduler.port.LogPort
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.MutualExclusionLockPort
import me.noukakis.re_do.scheduler.port.NowPort
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventStreamPort
import me.noukakis.re_do.scheduler.service.StreamTegEventsUseCase
import me.noukakis.re_do.scheduler.service.TEGScheduler
import me.noukakis.re_do.scheduler.service.UploadFileUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class SchedulerConfiguration {
    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "in-memory")
    fun messagingPortInMemoryBean(): MessagingPort = InMemoryMessagingAdapter()

    @Bean
    @ConditionalOnProperty(name = ["scheduler.persistence.mode"], havingValue = "in-memory")
    fun persistencePortBean(): InMemoryPersistenceAdapter = InMemoryPersistenceAdapter()

    @Bean
    fun nowPort(): NowPort = StdLibNowAdapter()

    @Bean
    fun uuidPort(): UUIDPort = StdLibUuidAdapter()

    @Bean
    fun logPort(): LogPort = Slf4jLogAdapter()

    @Bean
    fun schedulerBean(
        messagingPort: MessagingPort,
        persistencePort: PersistencePort,
        uuidPort: UUIDPort,
        nowPort: NowPort,
        logPort: LogPort,
        mutualExclusionLockPort: MutualExclusionLockPort,
        @Value("\${scheduler.max-failures-before-giving-up}") maxFailuresBeforeGivingUp: Int,
    ): TEGScheduler = TEGScheduler(
        messagingPort,
        persistencePort,
        uuidPort,
        nowPort,
        mutualExclusionLockPort,
        maxFailuresBeforeGivingUp,
        logPort,
    )

    @Bean
    @ConditionalOnProperty(name = ["scheduler.file-storage.mode"], havingValue = "in-memory")
    fun fileStoragePortBean(): FileStoragePort = InMemoryFileStorageAdapter()

    @Bean
    @ConditionalOnProperty(name = ["scheduler.file-reference-store.mode"], havingValue = "in-memory")
    fun fileReferenceStorePortBean(): FileReferenceStorePort = InMemoryFileReferenceStoreAdapter()

    @Bean
    @ConditionalOnProperty(name = ["scheduler.mutual-exclusion-lock.mode"], havingValue = "in-memory")
    fun mutualExclusionLockPortInMemoryBean(
        @Value("\${scheduler.mutual-exclusion-lock.timeout:30s}") lockTimeout: Duration,
    ): MutualExclusionLockPort = InMemoryMutualExclusionLockAdapter(lockTimeout)

    @Bean
    fun uploadFileUseCaseBean(
        fileStoragePort: FileStoragePort,
        fileReferenceStorePort: FileReferenceStorePort,
        uuidPort: UUIDPort,
    ): UploadFileUseCase = UploadFileUseCase(fileStoragePort, fileReferenceStorePort, uuidPort)

    @Bean
    fun streamTegEventsUseCaseBean(
        persistencePort: PersistencePort,
        tegEventStreamPort: TegEventStreamPort,
    ): StreamTegEventsUseCase = StreamTegEventsUseCase(persistencePort, tegEventStreamPort)
}
