package me.noukakis.re_do.adapters.driving.scheduler.spring.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.common.model.TaskProgress
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGEvent
import java.time.Instant

data class IdentityDTO(val sub: String, val roles: List<String>) {
    companion object {
        fun fromDomain(identity: Identity) = IdentityDTO(identity.sub, identity.roles)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TEGEventDTO.SubmitterIdentityDTO::class, name = "SubmitterIdentity"),
    JsonSubTypes.Type(value = TEGEventDTO.InitArtefactsDTO::class, name = "InitArtefacts"),
    JsonSubTypes.Type(value = TEGEventDTO.CreatedDTO::class, name = "Created"),
    JsonSubTypes.Type(value = TEGEventDTO.ScheduledDTO::class, name = "Scheduled"),
    JsonSubTypes.Type(value = TEGEventDTO.CompletedDTO::class, name = "Completed"),
    JsonSubTypes.Type(value = TEGEventDTO.NoMoreTasksToScheduleDTO::class, name = "NoMoreTasksToSchedule"),
    JsonSubTypes.Type(value = TEGEventDTO.TEGFailedDTO::class, name = "TEGFailed"),
    JsonSubTypes.Type(value = TEGEventDTO.FailedDTO::class, name = "Failed"),
    JsonSubTypes.Type(value = TEGEventDTO.ProgressDTO::class, name = "Progress"),
    JsonSubTypes.Type(value = TEGEventDTO.LogDTO::class, name = "Log"),
)
@Schema(
    description = "Base class for all TEG events. The 'type' field is used to determine the specific event type when deserializing from JSON.",
    subTypes = [
        TEGEventDTO.SubmitterIdentityDTO::class,
        TEGEventDTO.InitArtefactsDTO::class,
        TEGEventDTO.CreatedDTO::class,
        TEGEventDTO.ScheduledDTO::class,
        TEGEventDTO.CompletedDTO::class,
        TEGEventDTO.NoMoreTasksToScheduleDTO::class,
        TEGEventDTO.TEGFailedDTO::class,
        TEGEventDTO.FailedDTO::class,
        TEGEventDTO.ProgressDTO::class,
        TEGEventDTO.LogDTO::class,
    ],
    discriminatorProperty = "type",
    discriminatorMapping = [
        DiscriminatorMapping(value = "SubmitterIdentity", schema = TEGEventDTO.SubmitterIdentityDTO::class),
        DiscriminatorMapping(value = "InitArtefacts", schema = TEGEventDTO.InitArtefactsDTO::class),
        DiscriminatorMapping(value = "Created", schema = TEGEventDTO.CreatedDTO::class),
        DiscriminatorMapping(value = "Scheduled", schema = TEGEventDTO.ScheduledDTO::class),
        DiscriminatorMapping(value = "Completed", schema = TEGEventDTO.CompletedDTO::class),
        DiscriminatorMapping(value = "NoMoreTasksToSchedule", schema = TEGEventDTO.NoMoreTasksToScheduleDTO::class),
        DiscriminatorMapping(value = "TEGFailed", schema = TEGEventDTO.TEGFailedDTO::class),
        DiscriminatorMapping(value = "Failed", schema = TEGEventDTO.FailedDTO::class),
        DiscriminatorMapping(value = "Progress", schema = TEGEventDTO.ProgressDTO::class),
        DiscriminatorMapping(value = "Log", schema = TEGEventDTO.LogDTO::class),
    ],
)
sealed class TEGEventDTO {
    abstract val timestamp: Instant

    @Schema(name = "SubmitterIdentityEvent")
    data class SubmitterIdentityDTO(
        val identity: IdentityDTO,
        override val timestamp: Instant,
    ) : TEGEventDTO()

    @Schema(name = "InitArtefactsEvent")
    data class InitArtefactsDTO(
        val artefacts: List<TEGArtefactDTO>,
        override val timestamp: Instant,
    ) : TEGEventDTO()

    @Schema(name = "CreatedEvent")
    data class CreatedDTO(
        val task: TegTaskSummaryDTO,
        override val timestamp: Instant,
    ) : TEGEventDTO()

    @Schema(name = "ScheduledEvent")
    data class ScheduledDTO(
        val taskName: String,
        override val timestamp: Instant,
    ) : TEGEventDTO()

    @Schema(name = "CompletedEvent")
    data class CompletedDTO(
        val taskName: String,
        override val timestamp: Instant,
        val outputArtefacts: List<TEGArtefactDTO>,
    ) : TEGEventDTO()

    @Schema(name = "NoMoreTasksToScheduleEvent")
    data class NoMoreTasksToScheduleDTO(
        override val timestamp: Instant,
    ) : TEGEventDTO()

    @Schema(name = "TEGFailedEvent")
    data class TEGFailedDTO(
        override val timestamp: Instant,
        val reason: String,
    ) : TEGEventDTO()

    @Schema(name = "FailedEvent")
    data class FailedDTO(
        val taskName: String,
        override val timestamp: Instant,
        val reason: String,
    ) : TEGEventDTO()

    @Schema(name = "ProgressEvent")
    data class ProgressDTO(
        val taskName: String,
        override val timestamp: Instant,
        val progress: TaskProgressDTO,
    ) : TEGEventDTO()

    @Schema(name = "LogEvent")
    data class LogDTO(
        val taskName: String,
        override val timestamp: Instant,
        val log: String,
    ) : TEGEventDTO()

    companion object {
        fun fromDomain(event: TEGEvent): TEGEventDTO = when (event) {
            is TEGEvent.SubmitterIdentity -> SubmitterIdentityDTO(
                identity = IdentityDTO.fromDomain(event.identity),
                timestamp = event.timestamp,
            )

            is TEGEvent.InitArtefacts -> InitArtefactsDTO(
                artefacts = event.artefacts.map(::artefactToDto),
                timestamp = event.timestamp,
            )

            is TEGEvent.Created -> CreatedDTO(
                task = TegTaskSummaryDTO.fromDomain(event.task),
                timestamp = event.timestamp,
            )

            is TEGEvent.Scheduled -> ScheduledDTO(event.taskName, event.timestamp)

            is TEGEvent.Completed -> CompletedDTO(
                taskName = event.taskName,
                timestamp = event.timestamp,
                outputArtefacts = event.outputArtefacts.map(::artefactToDto),
            )

            is TEGEvent.NoMoreTasksToSchedule -> NoMoreTasksToScheduleDTO(event.timestamp)

            is TEGEvent.TEGFailed -> TEGFailedDTO(event.timestamp, event.reason)

            is TEGEvent.Failed -> FailedDTO(event.taskName, event.timestamp, event.reason)

            is TEGEvent.Progress -> ProgressDTO(event.taskName, event.timestamp, TaskProgressDTO.fromDomain(event.progress))

            is TEGEvent.Log -> LogDTO(event.taskName, event.timestamp, event.log)
        }

        private fun artefactToDto(artefact: TEGArtefact): TEGArtefactDTO = when (artefact) {
            is TEGArtefact.TEGArtefactStringValue -> TEGArtefactDTO.StringValueDTO(artefact.name, artefact.value)
            is TEGArtefact.TEGArtefactFile -> TEGArtefactDTO.FileDTO(artefact.name, artefact.ref, artefact.storedWith)
        }
    }
}

data class TegTaskSummaryDTO(
    val name: String,
    val implementationName: String,
) {
    companion object {
        fun fromDomain(task: TEGTask) = TegTaskSummaryDTO(task.name, task.implementationName)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = TaskProgressDTO.IndeterminateDTO::class, name = "indeterminate"),
    JsonSubTypes.Type(value = TaskProgressDTO.BoundedDTO::class, name = "bounded"),
    JsonSubTypes.Type(value = TaskProgressDTO.LlmTokensDTO::class, name = "llm_tokens"),
)
@Schema(
    description = "Progress payload. The 'kind' field selects the variant.",
    subTypes = [
        TaskProgressDTO.IndeterminateDTO::class,
        TaskProgressDTO.BoundedDTO::class,
        TaskProgressDTO.LlmTokensDTO::class,
    ],
    discriminatorProperty = "kind",
    discriminatorMapping = [
        DiscriminatorMapping(value = "indeterminate", schema = TaskProgressDTO.IndeterminateDTO::class),
        DiscriminatorMapping(value = "bounded", schema = TaskProgressDTO.BoundedDTO::class),
        DiscriminatorMapping(value = "llm_tokens", schema = TaskProgressDTO.LlmTokensDTO::class),
    ],
)
sealed class TaskProgressDTO {
    abstract val step: String

    @Schema(name = "IndeterminateProgress")
    data class IndeterminateDTO(override val step: String) : TaskProgressDTO()

    @Schema(name = "BoundedProgress")
    data class BoundedDTO(override val step: String, val percent: Int) : TaskProgressDTO()

    @Schema(name = "LlmTokensProgress")
    data class LlmTokensDTO(
        override val step: String,
        val inputTokens: Long,
        val outputTokens: Long,
    ) : TaskProgressDTO()

    companion object {
        fun fromDomain(progress: TaskProgress): TaskProgressDTO = when (progress) {
            is TaskProgress.Indeterminate -> IndeterminateDTO(progress.step)
            is TaskProgress.Bounded -> BoundedDTO(progress.step, progress.percent)
            is TaskProgress.LlmTokens -> LlmTokensDTO(progress.step, progress.inputTokens, progress.outputTokens)
        }
    }
}
