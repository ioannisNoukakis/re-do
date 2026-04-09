package me.noukakis.re_do.adapters.common.spring.mongodb.model

import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGArtefactType
import kotlin.time.Duration

data class MongodbTEGArtefactDefinition(
    val name: String,
    val type: TEGArtefactType,
) {
    fun toModel() = TEGArtefactDefinition(
        name = name,
        type = type,
    )
}

fun TEGArtefactDefinition.toMongoModel() = MongodbTEGArtefactDefinition(
    name = name,
    type = type,
)

data class MongodbTEGTask (
    val name: String,
    val implementationName: String,
    val inputs: List<MongodbTEGArtefactDefinition>,
    val outputs: List<MongodbTEGArtefactDefinition>,
    val arguments: List<String>,
    val timeout: Duration,
) {
    fun toModel() = TEGTask(
        name = name,
        implementationName = implementationName,
        inputs = inputs.map { it.toModel() },
        outputs = outputs.map { it.toModel() },
        arguments = arguments,
        timeout = timeout,
    )
}

fun TEGTask.toMongoModel() = MongodbTEGTask(
    name = name,
    implementationName = implementationName,
    inputs = inputs.map { it.toMongoModel() },
    outputs = outputs.map { it.toMongoModel() },
    arguments = arguments,
    timeout = timeout,
)