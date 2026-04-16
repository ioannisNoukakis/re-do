package me.noukakis.re_do.adapters.common.spring.mongodb.migrations

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbFileReference
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.dropCollection
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.indexOps

@ChangeUnit(id = "file-reference-initializer", order = "002", author = "Ioannis Noukakis")
class _002FileReferenceInitializerChange(
    private val mongoTemplate: MongoTemplate,
) {
    @Execution
    fun changeSet() {
        mongoTemplate.createCollection(MongodbFileReference::class.java)
        mongoTemplate.indexOps<MongodbFileReference>().run {
            createIndex(Index().on(MongodbFileReference::fileId.name, Sort.Direction.ASC).unique())
        }
    }

    @RollbackExecution
    fun rollback() {
        mongoTemplate.dropCollection<MongodbFileReference>()
    }
}
