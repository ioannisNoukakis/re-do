package me.noukakis.re_do.adapters.common.spring.mongodb.migrations

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbMutualExclusionLock
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.dropCollection
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.indexOps
import java.util.concurrent.TimeUnit

@ChangeUnit(id = "mutual-exclusion-lock", order = "003", author = "Ioannis Noukakis")
class _003MutualExclusionLockChange(
    private val mongoTemplate: MongoTemplate,
) {
    @Execution
    fun changeSet() {
        mongoTemplate.createCollection(MongodbMutualExclusionLock::class.java)
        mongoTemplate.indexOps<MongodbMutualExclusionLock>().createIndex(
            Index()
                .on(MongodbMutualExclusionLock::acquiredAt.name, Sort.Direction.ASC)
                .expire(5, TimeUnit.MINUTES)
        )
    }

    @RollbackExecution
    fun rollback() {
        mongoTemplate.dropCollection<MongodbMutualExclusionLock>()
    }
}