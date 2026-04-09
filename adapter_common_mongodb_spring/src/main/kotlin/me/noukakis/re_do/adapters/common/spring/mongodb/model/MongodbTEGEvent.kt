package me.noukakis.re_do.adapters.common.spring.mongodb.model

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.TEGEvent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("teg_events")
sealed class MongodbTEGEvent {
    abstract val id: String
    abstract val tegId: String
    abstract val type: String
    abstract val timestamp: Instant

    data class MongodbSubmitterIdentity(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.SubmitterIdentity::class.simpleName!!,
        override val timestamp: Instant,
        val sub: String,
        val roles: List<String>,
    ) : MongodbTEGEvent()

    data class MongodbCreated(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.Created::class.simpleName!!,
        override val timestamp: Instant,
        val task: MongodbTEGTask,
    ) : MongodbTEGEvent()

    data class MongodbScheduled(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.Scheduled::class.simpleName!!,
        override val timestamp: Instant,
        val taskName: String,
    ) : MongodbTEGEvent()

    data class MongodbCompleted(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.Completed::class.simpleName!!,
        override val timestamp: Instant,
        val taskName: String,
        val outputArtefacts: List<MongodbTEGArtefact>,
    ) : MongodbTEGEvent()

    data class MongodbNoMoreTasksToSchedule(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.NoMoreTasksToSchedule::class.simpleName!!,
        override val timestamp: Instant,
    ) : MongodbTEGEvent()

    data class MongodbTEGFailed(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.TEGFailed::class.simpleName!!,
        override val timestamp: Instant,
        val reason: String,
    ) : MongodbTEGEvent()

    data class MongodbFailed(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.Failed::class.simpleName!!,
        override val timestamp: Instant,
        val taskName: String,
        val reason: String,
    ) : MongodbTEGEvent()

    data class MongodbProgress(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.Progress::class.simpleName!!,
        override val timestamp: Instant,
        val taskName: String,
        val progress: Int,
    ) : MongodbTEGEvent()

    data class MongodbLog(
        @Id override val id: String,
        override val tegId: String,
        override val type: String = TEGEvent.Log::class.simpleName!!,
        override val timestamp: Instant,
        val taskName: String,
        val log: String,
    ) : MongodbTEGEvent()

    fun toModel() = when (this) {
        is MongodbSubmitterIdentity -> TEGEvent.SubmitterIdentity(
            identity = Identity(sub = sub, roles = roles),
            timestamp = timestamp,
        )

        is MongodbCreated -> TEGEvent.Created(
            task = task.toModel(),
            timestamp = timestamp,
        )

        is MongodbScheduled -> TEGEvent.Scheduled(
            taskName = taskName,
            timestamp = timestamp,
        )

        is MongodbCompleted -> TEGEvent.Completed(
            taskName = taskName,
            timestamp = timestamp,
            outputArtefacts = outputArtefacts.map { it.toModel() },
        )

        is MongodbNoMoreTasksToSchedule -> TEGEvent.NoMoreTasksToSchedule(
            timestamp = timestamp,
        )

        is MongodbTEGFailed -> TEGEvent.TEGFailed(
            timestamp = timestamp,
            reason = reason,
        )

        is MongodbFailed -> TEGEvent.Failed(
            taskName = taskName,
            timestamp = timestamp,
            reason = reason,
        )

        is MongodbProgress -> TEGEvent.Progress(
            taskName = taskName,
            timestamp = timestamp,
            progress = progress,
        )

        is MongodbLog -> TEGEvent.Log(
            taskName = taskName,
            timestamp = timestamp,
            log = log,
        )
    }
}

fun TEGEvent.toMongoModel(tegId: String, id: String): MongodbTEGEvent = when (this) {
    is TEGEvent.SubmitterIdentity -> MongodbTEGEvent.MongodbSubmitterIdentity(
        id = id,
        tegId = tegId,
        sub = identity.sub,
        roles = identity.roles,
        timestamp = timestamp,
    )

    is TEGEvent.Created -> MongodbTEGEvent.MongodbCreated(
        id = id,
        tegId = tegId,
        task = task.toMongoModel(),
        timestamp = timestamp,
    )

    is TEGEvent.Scheduled -> MongodbTEGEvent.MongodbScheduled(
        id = id,
        tegId = tegId,
        taskName = taskName,
        timestamp = timestamp,
    )

    is TEGEvent.Completed -> MongodbTEGEvent.MongodbCompleted(
        id = id,
        tegId = tegId,
        taskName = taskName,
        timestamp = timestamp,
        outputArtefacts = outputArtefacts.map { it.toMongoModel() },
    )

    is TEGEvent.NoMoreTasksToSchedule -> MongodbTEGEvent.MongodbNoMoreTasksToSchedule(
        id = id,
        tegId = tegId,
        timestamp = timestamp,
    )

    is TEGEvent.TEGFailed -> MongodbTEGEvent.MongodbTEGFailed(
        id = id,
        tegId = tegId,
        timestamp = timestamp,
        reason = reason,
    )

    is TEGEvent.Failed -> MongodbTEGEvent.MongodbFailed(
        id = id,
        tegId = tegId,
        taskName = taskName,
        timestamp = timestamp,
        reason = reason,
    )

    is TEGEvent.Progress -> MongodbTEGEvent.MongodbProgress(
        id = id,
        tegId = tegId,
        taskName = taskName,
        timestamp = timestamp,
        progress = progress,
    )

    is TEGEvent.Log -> MongodbTEGEvent.MongodbLog(
        id = id,
        tegId = tegId,
        taskName = taskName,
        timestamp = timestamp,
        log = log,
    )
}