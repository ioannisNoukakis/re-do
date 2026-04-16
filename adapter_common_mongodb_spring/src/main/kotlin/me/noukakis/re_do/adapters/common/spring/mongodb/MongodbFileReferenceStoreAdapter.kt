package me.noukakis.re_do.adapters.common.spring.mongodb

import me.noukakis.re_do.adapters.common.spring.mongodb.model.toMongoModel
import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.UUID

class MongodbFileReferenceStoreAdapter(
    private val mongoTemplate: MongoTemplate,
) : FileReferenceStorePort {
    override fun save(reference: FileReference) {
        mongoTemplate.insert(reference.toMongoModel(UUID.randomUUID().toString()))
    }
}
