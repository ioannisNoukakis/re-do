package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.adapters.driven.common.InMemoryMessagingAdapter
import me.noukakis.re_do.adapters.driven.common.StubUuidAdapter
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryPersistenceAdapter
import me.noukakis.re_do.adapters.driven.scheduler.adapter.SpyMutualExclusionLockAdapter
import me.noukakis.re_do.adapters.driven.scheduler.adapter.StubNowAdapter
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.scheduler.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Instant
import kotlin.time.Duration

val IDENTITY = Identity(
    sub = "user-123",
    roles = listOf("scheduler-user")
)

const val TEST_TEG_ID = "test-teg-id"

class TEGSchedulerSutBuilder {
    val messagingAdapter = InMemoryMessagingAdapter()
    val persistenceAdapter = InMemoryPersistenceAdapter()
    val uuidAdapter = StubUuidAdapter(listOf(TEST_TEG_ID))
    val nowAdapter = StubNowAdapter()
    val mutualExclusionLockAdapter = SpyMutualExclusionLockAdapter()
    var sut: TEGScheduler? = null

    lateinit var scheduleResult: Either<TegSchedulingError, String>
    lateinit var updateResult: Either<TegUpdateError, Unit>
    lateinit var timeoutCheckResult: Either<TegTimeoutCheckError, Unit>

    fun givenTheExistingEvents(state: Map<String, List<TEGEvent>>) {
        persistenceAdapter.state.putAll(state)
    }

    fun givenTheDatesToReturn(vararg timestamps: Instant) {
        nowAdapter.toReturn = timestamps.toMutableList()
    }

    fun givenThePersistenceThrows(errorMsg: String) {
        persistenceAdapter.throwOnPersist = errorMsg
    }

    fun givenTheGetEventsThrows(errorMsg: String) {
        persistenceAdapter.throwOnGetEvents = errorMsg
    }

    fun whenSubmittingTheTeg(tasks: List<TEGTask>, initArtefacts: List<TEGArtefact>) {
        createSut()
        scheduleResult = sut!!.scheduleTeg(ScheduleTEGCommand(IDENTITY, tasks, initArtefacts))
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
            expectedTegMessage.map { TEST_TEG_ID to it }.toList(),
            messagingAdapter.outgoingMessages,
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
            TEST_TEG_ID.right(),
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

    fun thenTheMutualExclusionLockWasCalledAndReleased() {
        assertEquals(listOf(TEST_TEG_ID), mutualExclusionLockAdapter.acquiredLocks)
        assertEquals(listOf(TEST_TEG_ID), mutualExclusionLockAdapter.releasedLocks)
    }

    fun thenTheTegHasAFailedEventWithReason(reason: String) {
        val events = persistenceAdapter.state[TEST_TEG_ID] ?: emptyList()
        val failedEvent = events.filterIsInstance<TEGEvent.TEGFailed>().lastOrNull()
        assertEquals(reason, failedEvent?.reason, "Expected a TEGFailed event with reason '$reason' but got: $failedEvent")
    }

    private fun createSut() {
        if (sut == null) {
            sut = TEGScheduler(
                messagingAdapter,
                persistenceAdapter,
                uuidAdapter,
                nowAdapter,
                mutualExclusionLockAdapter,
                3
            )
        }
    }
}

const val TEST_TASK_IMPL_NAME = "TestTaskImpl"
val TEG_TASK_INPUTS = listOf<TEGArtefactDefinition>()
val TEG_TASK_OUTPUTS = listOf<TEGArtefactDefinition>()
val TEG_TASK_ARGUMENTS = listOf<String>()
val TEG_TASK_TIMEOUT = Duration.parseOrNull("100d")!!

class TEGTaskBuilder(
    private val name: String
) {
    private var implementationName: String = TEST_TASK_IMPL_NAME
    private var inputs: List<TEGArtefactDefinition> = TEG_TASK_INPUTS
    private var outputs: List<TEGArtefactDefinition> = TEG_TASK_OUTPUTS
    private var arguments: List<String> = TEG_TASK_ARGUMENTS
    private var timeout: Duration = TEG_TASK_TIMEOUT

    fun withImplementation(implementationName: String): TEGTaskBuilder {
        this.implementationName = implementationName
        return this
    }

    fun withOutputs(vararg tegArtefactDefinition: TEGArtefactDefinition): TEGTaskBuilder {
        this.outputs = tegArtefactDefinition.toList()
        return this
    }

    fun withInputs(vararg tegArtefactDefinition: TEGArtefactDefinition): TEGTaskBuilder {
        this.inputs = tegArtefactDefinition.toList()
        return this
    }

    fun withArguments(vararg arguments: String): TEGTaskBuilder {
        this.arguments = arguments.toList()
        return this
    }

    fun withTimeout(timeout: Duration): TEGTaskBuilder {
        this.timeout = timeout
        return this
    }

    fun build(): TEGTask {
        return TEGTask(
            name = this.name,
            implementationName = this.implementationName,
            inputs = this.inputs,
            outputs = this.outputs,
            arguments = this.arguments,
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
    private var implementationName: String = TEST_TASK_IMPL_NAME,
    private var artefacts: List<TEGArtefact> = listOf(),
    private var arguments: List<String> = listOf(),
    private var timeout: Duration = TEG_TASK_TIMEOUT,
) {
    fun withImplementation(implementationName: String): RunTaskTEGMessageBuilder {
        this.implementationName = implementationName
        return this
    }

    fun withArtefacts(vararg artefacts: TEGArtefact): RunTaskTEGMessageBuilder {
        this.artefacts = artefacts.toList()
        return this
    }

    fun withArguments(vararg arguments: String): RunTaskTEGMessageBuilder {
        this.arguments = arguments.toList()
        return this
    }

    fun withTimeout(timeout: Duration): RunTaskTEGMessageBuilder {
        this.timeout = timeout
        return this
    }

    fun build(): TEGMessageOut.TEGRunTaskMessage {
        return TEGMessageOut.TEGRunTaskMessage(
            taskName = this.taskName,
            implementationName = this.implementationName,
            artefacts = this.artefacts,
            arguments = this.arguments,
            timeout = this.timeout,
        )
    }
}

val TEST_ARTEFACT_TYPE = TEGArtefactType.STRING_VALUE

class TEGArtefactDefBuilder(
    private val name: String,
    private var type: TEGArtefactType = TEST_ARTEFACT_TYPE
) {

    fun build(): TEGArtefactDefinition {
        return TEGArtefactDefinition(
            name = this.name,
            type = this.type,
        )
    }
}