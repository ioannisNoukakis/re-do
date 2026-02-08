package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.scheduler.adapter.InMemoryPersistenceAdapter
import me.noukakis.re_do.scheduler.adapter.SpyMessagingAdapter
import me.noukakis.re_do.scheduler.adapter.StubNowAdapter
import me.noukakis.re_do.scheduler.adapter.StubUuidAdapter
import me.noukakis.re_do.scheduler.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import java.time.Instant

const val TEST_TEG_ID = "test-teg-id"

class SchedulerSutBuilder {
    val messagingAdapter = SpyMessagingAdapter()
    val persistenceAdapter = InMemoryPersistenceAdapter()
    val uuidAdapter = StubUuidAdapter(TEST_TEG_ID)
    val nowAdapter = StubNowAdapter()
    var sut: TEGScheduler? = null

    lateinit var scheduleResult: Either<TegSchedulingError, Unit>
    lateinit var updateResult: Either<TegUpdateError, Unit>
    lateinit var timeoutCheckResult: Either<TegTimeoutCheckError, Unit>

    fun givenTheExistingEvents(state: Map<String, List<TEGEvent>>) {
        persistenceAdapter.state.putAll(state)
    }

    fun givenTheDatesToReturn(vararg timestamps: Instant) {
        nowAdapter.toReturn = timestamps.toMutableList()
    }

    fun whenSubmittingTheTeg(vararg tasks: TEGTask) {
        createSut()
        scheduleResult = sut!!.scheduleTeg(ScheduleTEGCommand(tasks.toList()))
    }

    fun whenGettingTegUpdate(message: TEGMessageIn) {
        createSut()
        updateResult = sut!!.handleTegUpdate(
            TEGUpdateCommand(
                tegId = TEST_TEG_ID,
                message = message
            )
        )
    }

    fun whenTheTimeoutCheckerRuns() {
        createSut()
        timeoutCheckResult = sut!!.runTimeoutCheck()
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

    fun thenTheUpdateResultIsAnError(expectedError: TegUpdateError) {
        assertEquals(
            expectedError.left(),
            updateResult,
        )
    }

    fun thenTheUpdateResultIsASuccess() {
        assertEquals(
            Unit.right(),
            updateResult,
        )
    }

    fun thenTheTimeoutCheckResultIsAnError(expectedError: TegTimeoutCheckError) {
        assertEquals(
            expectedError.left(),
            timeoutCheckResult,
        )
    }

    fun thenTheTimeoutCheckResultIsASuccess() {
        assertEquals(
            Unit.right(),
            timeoutCheckResult,
        )
    }

    private fun createSut() {
        if (sut == null) {
            sut = TEGScheduler(
                messagingAdapter,
                persistenceAdapter,
                uuidAdapter,
                nowAdapter,
                3
            )
        }
    }
}

val TEG_TASK_INPUTS = listOf<TEGArtefactDefinition>()
val TEG_TASK_OUTPUTS = listOf<TEGArtefactDefinition>()
val TEG_TASK_TIMEOUT = Duration.ofDays(100)

class TEGTaskBuilder(
    private val name: String
) {
    private var inputs: List<TEGArtefactDefinition> = TEG_TASK_INPUTS
    private var outputs: List<TEGArtefactDefinition> = TEG_TASK_OUTPUTS
    private var timeout: Duration = TEG_TASK_TIMEOUT

    fun withOutputs(vararg tegArtefactDefinition: TEGArtefactDefinition): TEGTaskBuilder {
        this.outputs = tegArtefactDefinition.toList()
        return this
    }

    fun withInputs(vararg tegArtefactDefinition: TEGArtefactDefinition): TEGTaskBuilder {
        this.inputs = tegArtefactDefinition.toList()
        return this
    }

    fun withTimeout(timeout: Duration): TEGTaskBuilder {
        this.timeout = timeout
        return this
    }

    fun build(): TEGTask {
        return TEGTask(
            name = this.name,
            inputs = this.inputs,
            outputs = this.outputs,
            timeout = this.timeout,
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

    fun build(): TEGArtefactDefinition {
        return TEGArtefactDefinition(
            name = this.name,
            type = this.type,
        )
    }
}