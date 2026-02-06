package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.scheduler.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class TEGSchedulerTest {
    private lateinit var sut: SchedulerSutBuilder

    @BeforeEach
    fun setup() {
        sut = SchedulerSutBuilder()
    }

    @Nested
    inner class ScheduleTaskExecutionGraph {
        @Test
        fun `an empty TEG should not schedule any tasks`() {
            sut.whenSubmittingTheTeg()

            sut.thenTheScheduledTasksAre()
        }

        @Test
        fun `an empty TEG should result in an error`() {
            sut.whenSubmittingTheTeg()

            sut.thenTheResultIsAnError(TegSchedulingError.EmptyTegNotAllowed)
        }

        @Test
        fun `should schedule tasks without errors`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .build(),
            )

            sut.thenTheResultIsASuccess()
        }

        @Test
        fun `should schedule tasks that can immediately run`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .build()
            )

            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build(),
            )
        }

        @Test
        fun `should save the resulting events in persistence`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .withOutputs(
                                    TEGArtefactDefinition(
                                        name = "AOutput",
                                        type = TEGArtefactType.STRING_VALUE
                                    )
                                )
                                .build()
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A"
                        ),
                        TEGEvent.Created(
                            TEGTaskBuilder("B")
                                .withInputs(
                                    TEGArtefactDefinition(
                                        name = "AOutput",
                                        type = TEGArtefactType.STRING_VALUE
                                    )
                                )
                                .build()
                        ),
                    )
                )
            )
        }

        @Test
        fun `should error out if no task has no inputs and thus cannot start the execution`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
            )

            sut.thenTheResultIsAnError(
                TegSchedulingError.NoStartingTaskFound
            )
        }

        @Test
        fun `should detect missing artefact producers in the TEG and error out`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(
                        TEGArtefactDefBuilder("NonExistentOutput").build(),
                        TEGArtefactDefBuilder("AOutput").build(),
                    )
                    .build(),
            )

            sut.thenTheResultIsAnError(
                TegSchedulingError.MissingArtefactProducer(
                    taskName = "B",
                    artefactName = "NonExistentOutput"
                )
            )
        }

        @ParameterizedTest
        @MethodSource("me.noukakis.re_do.scheduler.service.TEGSchedulerTest#cycleProvider")
        fun `should detect any cycle in the task execution graph and error out`(
            tasks: List<TEGTask>,
            expectedError: TegSchedulingError.CyclicDependencyDetected
        ) {
            sut.whenSubmittingTheTeg(*tasks.toTypedArray())

            sut.thenTheResultIsAnError(expectedError)
        }

        @Test
        fun `should detect if two tasks have the same name and error out`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("A")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
            )

            sut.thenTheResultIsAnError(
                TegSchedulingError.TasksHaveTheSameName(
                    taskName = "A",
                )
            )
        }

        @Test
        fun `should detect if two artefacts have the same name produced by different tasks and error out`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("CommonOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withOutputs(TEGArtefactDefBuilder("CommonOutput").build())
                    .build(),
            )

            sut.thenTheResultIsAnError(
                TegSchedulingError.TasksProduceSameArtefactName(
                    taskNames = listOf("A", "B"),
                    artefactName = "CommonOutput",
                )
            )
        }

        @Test
        fun `should detect and error out if an ouput artefact is never consumed`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(
                        TEGArtefactDefBuilder("AOutput").build(),
                        TEGArtefactDefBuilder("oooooooooo").build(),
                    )
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
            )

            sut.thenTheResultIsAnError(
                TegSchedulingError.NotAllProducedArtefactsAreConsumed(
                    producingTaskName = "A",
                    artefactName = "oooooooooo"
                )
            )
        }
    }

    companion object {
        @JvmStatic
        fun cycleProvider(): Stream<Arguments> = Stream.of(
            // Test case 1: 3-node cycle (A -> B -> C -> A)
            Arguments.of(
                listOf(
                    TEGTaskBuilder("IN")
                        .withOutputs(TEGArtefactDefBuilder("INOutput").build())
                        .build(),
                    TEGTaskBuilder("A")
                        .withInputs(
                            TEGArtefactDefBuilder("INOutput").build(),
                            TEGArtefactDefBuilder("COutput").build(),
                        )
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .build(),
                    TEGTaskBuilder("B")
                        .withInputs(TEGArtefactDefBuilder("AOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .build(),
                    TEGTaskBuilder("C")
                        .withInputs(TEGArtefactDefBuilder("BOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("COutput").build())
                        .build(),
                ),
                TegSchedulingError.CyclicDependencyDetected(
                    listOf("A", "B", "C", "A")
                )
            ),
            // Test case 2: Simple 2-node cycle (A -> B -> A)
            Arguments.of(
                listOf(
                    TEGTaskBuilder("IN")
                        .withOutputs(TEGArtefactDefBuilder("INOutput").build())
                        .build(),
                    TEGTaskBuilder("A")
                        .withInputs(
                            TEGArtefactDefBuilder("INOutput").build(),
                            TEGArtefactDefBuilder("BOutput").build()
                        )
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .build(),
                    TEGTaskBuilder("B")
                        .withInputs(TEGArtefactDefBuilder("AOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .build(),
                ),
                TegSchedulingError.CyclicDependencyDetected(
                    listOf("A", "B", "A")
                )
            ),
            // Test case 4: Longer cycle (A -> B -> C -> D -> A)
            Arguments.of(
                listOf(
                    TEGTaskBuilder("IN")
                        .withOutputs(TEGArtefactDefBuilder("INOutput").build())
                        .build(),
                    TEGTaskBuilder("A")
                        .withInputs(
                            TEGArtefactDefBuilder("INOutput").build(),
                            TEGArtefactDefBuilder("DOutput").build(),
                        )
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .build(),
                    TEGTaskBuilder("B")
                        .withInputs(TEGArtefactDefBuilder("AOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .build(),
                    TEGTaskBuilder("C")
                        .withInputs(TEGArtefactDefBuilder("BOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("COutput").build())
                        .build(),
                    TEGTaskBuilder("D")
                        .withInputs(TEGArtefactDefBuilder("COutput").build())
                        .withOutputs(TEGArtefactDefBuilder("DOutput").build())
                        .build(),
                ),
                TegSchedulingError.CyclicDependencyDetected(
                    listOf("A", "B", "C", "D", "A")
                )
            ),
            // Test case 5: Multiple cycles (A -> B -> A and C -> D -> C)
            Arguments.of(
                listOf(
                    TEGTaskBuilder("IN")
                        .withOutputs(TEGArtefactDefBuilder("INOutput").build())
                        .build(),
                    TEGTaskBuilder("A")
                        .withInputs(
                            TEGArtefactDefBuilder("INOutput").build(),
                            TEGArtefactDefBuilder("BOutput").build(),
                        )
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .build(),
                    TEGTaskBuilder("B")
                        .withInputs(TEGArtefactDefBuilder("AOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .build(),
                    TEGTaskBuilder("C")
                        .withInputs(
                            TEGArtefactDefBuilder("INOutput").build(),
                            TEGArtefactDefBuilder("DOutput").build(),
                        )
                        .withOutputs(TEGArtefactDefBuilder("COutput").build())
                        .build(),
                    TEGTaskBuilder("D")
                        .withInputs(TEGArtefactDefBuilder("COutput").build())
                        .withOutputs(TEGArtefactDefBuilder("DOutput").build())
                        .build(),
                ),
                TegSchedulingError.CyclicDependencyDetected(
                    listOf("A", "B", "A")
                )
            ),
            // Test case 6: Complex cycle with branching (A -> B -> C -> D -> B)
            Arguments.of(
                listOf(
                    TEGTaskBuilder("IN")
                        .withOutputs(TEGArtefactDefBuilder("INOutput").build())
                        .build(),
                    TEGTaskBuilder("A")
                        .withInputs(TEGArtefactDefBuilder("INOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .build(),
                    TEGTaskBuilder("B")
                        .withInputs(
                            TEGArtefactDefBuilder("AOutput").build(),
                            TEGArtefactDefBuilder("DOutput").build(),
                        )
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .build(),
                    TEGTaskBuilder("C")
                        .withInputs(TEGArtefactDefBuilder("BOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("COutput").build())
                        .build(),
                    TEGTaskBuilder("D")
                        .withInputs(TEGArtefactDefBuilder("COutput").build())
                        .withOutputs(TEGArtefactDefBuilder("DOutput").build())
                        .build(),
                ),
                TegSchedulingError.CyclicDependencyDetected(
                    listOf("B", "C", "D", "B")
                )
            ),
        )
    }

    @Nested
    inner class HandleWorkerResultMessage {
        lateinit var baseEvents: List<TEGEvent>

        @BeforeEach
        fun setup() {
            baseEvents = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withOutputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build()
                ),
                TEGEvent.Scheduled(
                    taskName = "A"
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build()
                ),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "A",
                    listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                )
            )
        }

        @Test
        fun `on task completion, dependent tasks should be scheduled`() {
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("B")
                    .asRunType()
                    .withArtefacts(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                    .build()
            )
        }

        @Test
        fun `on task completion the resulting events should be saved in persistence`() {
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to baseEvents + listOf(
                        TEGEvent.Completed(
                            taskName = "A",
                            outputArtefacts = listOf(
                                TEGArtefact.TEGArtefactStringValue(
                                    name = "AOutput",
                                    value = "result of A"
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Nested
    inner class HandleWorkerFailureMessage {
        lateinit var baseEvents: List<TEGEvent>

        @BeforeEach
        fun setup() {
            baseEvents = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withOutputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build()
                ),
                TEGEvent.Scheduled(
                    taskName = "A"
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build()
                ),
            )
        }

        @Test
        fun `on task failure, the task should be rescheduled`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Worker crashed"
                )
            )

            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build()
            )
        }

        @Test
        fun `on task failure, the failure event and reschedule event should be persisted`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Worker crashed"
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to baseEvents + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            reason = "Worker crashed"
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A"
                        )
                    )
                )
            )
        }

        @Test
        fun `on third failure for the same task, should abort and return max retries exceeded error`() {
            val eventsWithTwoFailures = baseEvents + listOf(
                TEGEvent.Failed(taskName = "A", reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A"),
                TEGEvent.Failed(taskName = "A", reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A"),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithTwoFailures))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Third failure"
                )
            )

            sut.thenTheUpdateResultIsAnError(
                TegSchedulingError.MaxRetriesExceeded(taskName = "A")
            )
        }

        @Test
        fun `on third failure, the task should not be rescheduled`() {
            val eventsWithTwoFailures = baseEvents + listOf(
                TEGEvent.Failed(taskName = "A", reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A"),
                TEGEvent.Failed(taskName = "A", reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A"),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithTwoFailures))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Third failure"
                )
            )

            sut.thenTheScheduledTasksAre()
        }

        @Test
        fun `on third failure, the failure event should be persisted but no reschedule event`() {
            val eventsWithTwoFailures = baseEvents + listOf(
                TEGEvent.Failed(taskName = "A", reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A"),
                TEGEvent.Failed(taskName = "A", reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A"),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithTwoFailures))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Third failure"
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to eventsWithTwoFailures + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            reason = "Third failure"
                        )
                    )
                )
            )
        }
    }

    @Nested
    inner class HandleOtherMessages {
        @Test
        fun `on progress message, this event should be persisted`() {
            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskProgressMessage(
                    taskName = "A",
                    progress = 50
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Progress(
                            taskName = "A",
                            progress = 50
                        )
                    )
                )
            )
        }

        @Test
        fun `on log message, this event should be persisted`() {
            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskLogMessage(
                    taskName = "A",
                    log = "This is a log message"
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Log(
                            taskName = "A",
                            log = "This is a log message"
                        )
                    )
                )
            )
        }
    }

    // TODO timeouts?
}