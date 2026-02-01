package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.scheduler.adapter.InMemoryPersistenceAdapter
import me.noukakis.re_do.scheduler.adapter.SpyMessagingAdapter
import me.noukakis.re_do.scheduler.adapter.StubUuidAdapter
import me.noukakis.re_do.scheduler.model.*
import org.junit.jupiter.api.Assertions.assertEquals

const val TEST_TEG_ID = "test-teg-id"

class SchedulerSutBuilder {
    val messagingAdapter = SpyMessagingAdapter()
    val persistenceAdapter = InMemoryPersistenceAdapter()
    val uuidAdapter = StubUuidAdapter(TEST_TEG_ID)
    var sut: TEGScheduler? = null

    lateinit var scheduleResult: Either<TegSchedulingError, Unit>

    fun givenTheExistingEvents(state: Map<String, List<TEGEvent>>) {
        persistenceAdapter.state.putAll(state)
    }

    fun whenSubmittingTheTeg(vararg tasks: TEGTask) {
        createSut()
        scheduleResult = sut!!.scheduleTeg(ScheduleTEGCommand(tasks.toList()))
    }

    fun whenGettingTegUpdate(message: TEGMessageIn) {
        createSut()
        sut!!.handleTegUpdate(
            TEGUpdateCommand(
                tegId = TEST_TEG_ID,
                message = message
            )
        )
    }

    fun thenTheScheduledTasksAre(vararg expectedTegMessage: TEGMessageOut) {
        assertEquals(
            expectedTegMessage.toList(),
            messagingAdapter.sentMessages,
        )
    }

    fun thenThePersistedEventsShouldBe(expectedPersistenceState: Map<String, List<TEGEvent>>) {
        assertEquals(
            expectedPersistenceState,
            persistenceAdapter.state,
        )
    }

    fun thenTheResultIsAnError(emptyTegNotAllowed: TegSchedulingError) {
        assertEquals(
            emptyTegNotAllowed.left(),
            scheduleResult,
        )
    }

    fun thenTheResultIsASuccess() {
        assertEquals(
            Unit.right(),
            scheduleResult,
        )
    }

    private fun createSut() {
        if (sut == null) {
            sut = TEGScheduler(messagingAdapter, persistenceAdapter, uuidAdapter)
        }
    }
}

val TEG_TASK_INPUTS = listOf<TEGArtefactDefinition>()
val TEG_TASK_OUTPUTS = listOf<TEGArtefactDefinition>()

class TEGTaskBuilder(
    private val name: String
) {
    private var inputs: List<TEGArtefactDefinition> = TEG_TASK_INPUTS
    private var outputs: List<TEGArtefactDefinition> = TEG_TASK_OUTPUTS

    fun withOutputs(vararg tegArtefactDefinition: TEGArtefactDefinition): TEGTaskBuilder {
        this.outputs = tegArtefactDefinition.toList()
        return this
    }

    fun withInputs(vararg tegArtefactDefinition: TEGArtefactDefinition): TEGTaskBuilder {
        this.inputs = tegArtefactDefinition.toList()
        return this
    }

    fun build(): TEGTask {
        return TEGTask(
            name = this.name,
            inputs = this.inputs,
            outputs = this.outputs
        )
    }
}

class TEGMessageBuilder(
    private val taskName: String
) {
    fun asRunType(): RunTaskTEGMessageBuilder {
        return RunTaskTEGMessageBuilder(taskName)
    }
}

class RunTaskTEGMessageBuilder(
    private val taskName: String,
    private var artefacts: List<TEGArtefact> = listOf(),
) {
    fun withArtefacts(vararg artefacts: TEGArtefact): RunTaskTEGMessageBuilder {
        this.artefacts = artefacts.toList()
        return this
    }

    fun build(): TEGMessageOut.TEGRunTaskMessage {
        return TEGMessageOut.TEGRunTaskMessage(
            taskName = this.taskName,
            artefacts = this.artefacts,
        )
    }
}

val TEST_ARTEFACT_TYPE = TEGArtefactType.STRING_VALUE

class TEGArtefactDefBuilder(
    private val name: String,
) {
    private var type: TEGArtefactType = TEST_ARTEFACT_TYPE

    fun withType(type: TEGArtefactType): TEGArtefactDefBuilder {
        this.type = type
        return this
    }

    fun build(): TEGArtefactDefinition {
        return TEGArtefactDefinition(
            name = this.name,
            type = this.type,
        )
    }
}