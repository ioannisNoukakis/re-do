package me.noukakis.re_do.adapters.common.spring.mongodb

import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbMutualExclusionLock
import me.noukakis.re_do.scheduler.port.LockTimeoutException
import me.noukakis.re_do.scheduler.port.MutualExclusionLockPort
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration
import java.time.Instant

class MongoMutualExclusionLockAdapter(
    private val mongoTemplate: MongoTemplate,
    private val getNow: () -> Instant,
    val retryInterval: Duration = Duration.ofMillis(100),
    val lockTimeout: Duration = Duration.ofSeconds(30),
) : MutualExclusionLockPort {

    private val logger = LoggerFactory.getLogger(MongoMutualExclusionLockAdapter::class.java)

    override fun lock(tegId: String) {
        val startedAt = getNow()
        while (!Thread.currentThread().isInterrupted) {
            try {
                mongoTemplate.insert(MongodbMutualExclusionLock(id = tegId, acquiredAt = getNow()))
                return
            } catch (_: DuplicateKeyException) {
                logger.debug("Lock for tegId={} is already held, retrying in {}", tegId, retryInterval)
                if (Duration.between(startedAt, getNow()) >= lockTimeout) {
                    throw LockTimeoutException(tegId, lockTimeout)
                }
                try {
                    Thread.sleep(retryInterval.toMillis())
                } catch (_: InterruptedException) {
                    // https://web.archive.org/web/20210301203607/http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html?ca=drs-
                    Thread.currentThread().interrupt() // restore the flag
                }
            }
        }
    }

    override fun release(tegId: String) {
        mongoTemplate.remove(
            Query.query(Criteria.where("_id").`is`(tegId)),
            MongodbMutualExclusionLock::class.java
        )
    }
}