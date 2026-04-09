package me.noukakis.re_do.adapters.common.spring.mongodb.migrations

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbTEGEvent
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.dropCollection
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.indexOps
import java.util.concurrent.TimeUnit

@ChangeUnit(id="teg-event-initializer", order = "001", author = "Ioannis Noukakis")
class _001TegEventInitializerChange(
    private val mongoTemplate: MongoTemplate,
) {
    @Execution
    fun changeSet() {
        mongoTemplate.createCollection(MongodbTEGEvent::class.java)
        mongoTemplate.indexOps<MongodbTEGEvent>().run {
            createIndex(Index().on(MongodbTEGEvent::tegId.name, Sort.Direction.ASC))
            createIndex(
                Index().on(MongodbTEGEvent::timestamp.name, Sort.Direction.ASC)
                    .expire(30, TimeUnit.DAYS)
            )
        }
    }

    @RollbackExecution
    fun rollback() {
        mongoTemplate.dropCollection<MongodbTEGEvent>()
    }
}