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

    fun whenSubmittingTheTeg(vararg tasks: TEGTask) {
        if (sut == null) {
            sut = TEGScheduler(messagingAdapter, persistenceAdapter, uuidAdapter)
        }
        scheduleResult = sut!!.scheduleTeg(ScheduleTEGCommand(tasks.toList()))
    }

    fun thenTheScheduledTasksAre(vararg expectedTegMessage: TEGMessage) {
        assertEquals(
            expectedTegMessage.toList(),
            messagingAdapter.sentMessages,
        )
    }

    fun thenTheDependencyMapIsSavedCorrectly(expectedPersistenceState: Map<String, Map<TEGDependencyKey, TEGArtefact?>>) {
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