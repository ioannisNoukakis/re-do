package me.noukakis.re_do.adapters.common.spring.mongodb.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("mutual_exclusion_lock")
data class MongodbMutualExclusionLock(
    @Id val id: String,
    val acquiredAt: Instant,
)