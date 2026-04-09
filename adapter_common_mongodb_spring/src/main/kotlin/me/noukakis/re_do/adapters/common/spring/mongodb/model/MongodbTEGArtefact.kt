package me.noukakis.re_do.adapters.common.spring.mongodb.model

import me.noukakis.re_do.scheduler.model.TEGArtefact

sealed class MongodbTEGArtefact {
    abstract val name: String

    data class MongoTEGArtefactFile(
        override val name: String,
        val ref: String,
        val storedWith: String,
    ) : MongodbTEGArtefact()

    data class MongoTEGArtefactStringValue(
        override val name: String,
        val value: String,
    ) : MongodbTEGArtefact()

    fun toModel() = when (this) {
        is MongoTEGArtefactFile -> TEGArtefact.TEGArtefactFile(
            name = name,
            ref = ref,
            storedWith = storedWith,
        )
        is MongoTEGArtefactStringValue -> TEGArtefact.TEGArtefactStringValue(
            name = name,
            value = value,
        )
    }
}

fun TEGArtefact.toMongoModel(): MongodbTEGArtefact = when (this) {
    is TEGArtefact.TEGArtefactFile -> MongodbTEGArtefact.MongoTEGArtefactFile(
        name = name,
        ref = ref,
        storedWith = storedWith,
    )
    is TEGArtefact.TEGArtefactStringValue -> MongodbTEGArtefact.MongoTEGArtefactStringValue(
        name = name,
        value = value,
    )
}