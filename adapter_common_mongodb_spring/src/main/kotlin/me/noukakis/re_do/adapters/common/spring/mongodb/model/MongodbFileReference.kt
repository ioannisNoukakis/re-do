package me.noukakis.re_do.adapters.common.spring.mongodb.model

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.FileReference
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("file_references")
data class MongodbFileReference(
    @Id val id: String,
    val fileId: String,
    val ref: String,
    val storedWith: String,
    val uploadedBySub: String,
    val uploadedByRoles: List<String>,
) {
    fun toModel(): FileReference = FileReference(
        fileId = fileId,
        ref = ref,
        storedWith = storedWith,
        uploadedBy = Identity(sub = uploadedBySub, roles = uploadedByRoles),
    )
}

fun FileReference.toMongoModel(id: String) = MongodbFileReference(
    id = id,
    fileId = fileId,
    ref = ref,
    storedWith = storedWith,
    uploadedBySub = uploadedBy.sub,
    uploadedByRoles = uploadedBy.roles,
)
