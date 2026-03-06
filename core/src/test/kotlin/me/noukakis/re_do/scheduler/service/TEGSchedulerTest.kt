package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.scheduler.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.time.Instant
import java.util.stream.Stream

val NOW_0: Instant = Instant.ofEpochMilli(0)
val NOW_1: Instant = Instant.ofEpochMilli(1)
val NOW_2: Instant = Instant.ofEpochMilli(2)
val NOW_3: Instant = Instant.ofEpochMilli(3)
val NOW_4: Instant = Instant.ofEpochMilli(4)
val NOW_5: Instant = Instant.ofEpochMilli(5)
val NOW_6: Instant = Instant.ofEpochMilli(6)
val NOW_12: Instant = Instant.ofEpochMilli(12)

class TEGSchedulerTest {
    private lateinit var sut: TEGSchedulerSutBuilder

    @BeforeEach
    fun setup() {
        sut = TEGSchedulerSutBuilder()
    }

    @Nested
    inner class ScheduleTaskExecutionGraph {

        // TODO
        // Empty TEGMessageIn.TEGTaskResultMessage.outputArtefacts when outputs are expected
        // Mismatched artefact types between producer and consumer
        // Receiving messages for non-existent TEG ID
        // Receiving messages for a TEG that's already marked as NoMoreTasksToSchedule or TEGFailed
        // Tasks with missing implementations (if we want to validate that at scheduling time and not at worker level)
        @BeforeEach
        fun setup() {
            sut.givenTheDatesToReturn(NOW_0)
        }

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
                    .withImplementation("ImplA")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .withArguments("arg1", "arg2")
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .build()
            )

            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .withImplementation("ImplA")
                    .withArguments("arg1", "arg2")
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
                        TEGEvent.SubmitterIdentity(IDENTITY, NOW_0),
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .withOutputs(
                                    TEGArtefactDefinition(
                                        name = "AOutput",
                                        type = TEGArtefactType.STRING_VALUE
                                    )
                                )
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_0,
                        ),
                        TEGEvent.Created(
                            TEGTaskBuilder("B")
                                .withInputs(
                                    TEGArtefactDefinition(
                                        name = "AOutput",
                                        type = TEGArtefactType.STRING_VALUE
                                    )
                                )
                                .build(),
                            NOW_0,
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
        fun `should detect and error out if an output artefact is never consumed`() {
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

        @Test
        fun `task with multiple outputs consumed by different dependent tasks should be valid`() {
            // A produces two artefacts, B consumes one and C consumes the other
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(
                        TEGArtefactDefBuilder("AOutput1").build(),
                        TEGArtefactDefBuilder("AOutput2").build(),
                    )
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefBuilder("AOutput1").build())
                    .build(),
                TEGTaskBuilder("C")
                    .withInputs(TEGArtefactDefBuilder("AOutput2").build())
                    .build(),
            )

            sut.thenTheResultIsASuccess()
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build(),
            )
        }

        @Test
        fun `should schedule multiple starting tasks simultaneously`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                    .build(),
                TEGTaskBuilder("C")
                    .withInputs(
                        TEGArtefactDefBuilder("AOutput").build(),
                        TEGArtefactDefBuilder("BOutput").build(),
                    )
                    .build(),
            )

            sut.thenTheResultIsASuccess()
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build(),
                TEGMessageBuilder("B")
                    .asRunType()
                    .build(),
            )
        }

        @Test
        fun `should persist events for multiple starting tasks`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                    .build(),
                TEGTaskBuilder("C")
                    .withInputs(
                        TEGArtefactDefBuilder("AOutput").build(),
                        TEGArtefactDefBuilder("BOutput").build(),
                    )
                    .build(),
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.SubmitterIdentity(IDENTITY, NOW_0),
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(taskName = "A", timestamp = NOW_0),
                        TEGEvent.Created(
                            TEGTaskBuilder("B")
                                .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(taskName = "B", timestamp = NOW_0),
                        TEGEvent.Created(
                            TEGTaskBuilder("C")
                                .withInputs(
                                    TEGArtefactDefBuilder("AOutput").build(),
                                    TEGArtefactDefBuilder("BOutput").build(),
                                )
                                .build(),
                            NOW_0,
                        ),
                    )
                )
            )
        }

        @Test
        fun `diamond dependency pattern should be valid`() {
            // A -> B, A -> C, B -> D, C -> D (diamond shape)
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                    .build(),
                TEGTaskBuilder("C")
                    .withInputs(TEGArtefactDefBuilder("AOutput").build())
                    .withOutputs(TEGArtefactDefBuilder("COutput").build())
                    .build(),
                TEGTaskBuilder("D")
                    .withInputs(
                        TEGArtefactDefBuilder("BOutput").build(),
                        TEGArtefactDefBuilder("COutput").build(),
                    )
                    .build(),
            )

            sut.thenTheResultIsASuccess()
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build(),
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
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    NOW_0,
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build(),
                    NOW_0,
                ),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))
            sut.givenTheDatesToReturn(NOW_1)

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
                            timestamp = NOW_1,
                            outputArtefacts = listOf(
                                TEGArtefact.TEGArtefactStringValue(
                                    name = "AOutput",
                                    value = "result of A"
                                )
                            ),
                        ),
                    )
                )
            )
        }
    }

    @Nested
    inner class HandleWorkerResultMessageWithMultipleOutputs {
        @Test
        fun `task with multiple outputs should schedule dependents with only the artefacts they need`() {
            // A produces two artefacts, B needs AOutput1, C needs AOutput2
            val eventsWithMultipleOutputs = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withOutputs(
                            TEGArtefactDefBuilder("AOutput1").build(),
                            TEGArtefactDefBuilder("AOutput2").build(),
                        )
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_0),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(TEGArtefactDefBuilder("AOutput1").build())
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("C")
                        .withInputs(TEGArtefactDefBuilder("AOutput2").build())
                        .build(),
                    NOW_0,
                ),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithMultipleOutputs))
            sut.givenTheDatesToReturn(NOW_1)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "A",
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput1", value = "result 1"),
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput2", value = "result 2"),
                    )
                )
            )

            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("B")
                    .asRunType()
                    .withArtefacts(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput1", value = "result 1"),
                    )
                    .build(),
                TEGMessageBuilder("C")
                    .asRunType()
                    .withArtefacts(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput2", value = "result 2"),
                    )
                    .build(),
            )
        }
    }

    @Nested
    inner class TegCompletion {
        @Test
        fun `single task`() {
            sut.givenTheExistingEvents(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_0,
                        ),
                    )
                )
            )
            sut.givenTheDatesToReturn(NOW_1)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "A",
                    outputArtefacts = listOf()
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_0,
                        ),
                        TEGEvent.Completed(
                            taskName = "A",
                            timestamp = NOW_1,
                            outputArtefacts = listOf(),
                        ),
                        TEGEvent.NoMoreTasksToSchedule(
                            timestamp = NOW_1,
                        )
                    )
                )
            )
        }

        @Test
        fun `multiple tasks with dependencies`() {
            sut.givenTheExistingEvents(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_0,
                        ),
                        TEGEvent.Created(
                            TEGTaskBuilder("B")
                                .withInputs(TEGArtefactDefBuilder("AOutput").build())
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_1,
                            reason = "Worker crashed"
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_1,
                        ),
                        TEGEvent.Completed(
                            taskName = "A",
                            timestamp = NOW_2,
                            outputArtefacts = listOf(
                                TEGArtefact.TEGArtefactStringValue(
                                    name = "AOutput",
                                    value = "result of A"
                                )
                            ),
                        ),
                        TEGEvent.Scheduled(
                            taskName = "B",
                            NOW_2,
                        ),
                    )
                )
            )
            sut.givenTheDatesToReturn(NOW_3)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "B",
                    outputArtefacts = listOf()
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to listOf(
                        TEGEvent.Created(
                            TEGTaskBuilder("A")
                                .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_0,
                        ),
                        TEGEvent.Created(
                            TEGTaskBuilder("B")
                                .withInputs(TEGArtefactDefBuilder("AOutput").build())
                                .build(),
                            NOW_0,
                        ),
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_1,
                            reason = "Worker crashed"
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            NOW_1,
                        ),
                        TEGEvent.Completed(
                            taskName = "A",
                            timestamp = NOW_2,
                            outputArtefacts = listOf(
                                TEGArtefact.TEGArtefactStringValue(
                                    name = "AOutput",
                                    value = "result of A"
                                )
                            ),
                        ),
                        TEGEvent.Scheduled(
                            taskName = "B",
                            NOW_2,
                        ),
                        TEGEvent.Completed(
                            taskName = "B",
                            timestamp = NOW_3,
                            outputArtefacts = listOf(),
                        ),
                        TEGEvent.NoMoreTasksToSchedule(
                            timestamp = NOW_3,
                        )
                    )
                )
            )
        }
    }

    @Nested
    inner class DiamondDependencyPattern {
        // Diamond: A -> B, A -> C, B -> D, C -> D
        lateinit var baseEvents: List<TEGEvent>

        @BeforeEach
        fun setup() {
            baseEvents = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_0),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(TEGArtefactDefBuilder("AOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("C")
                        .withInputs(TEGArtefactDefBuilder("AOutput").build())
                        .withOutputs(TEGArtefactDefBuilder("COutput").build())
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("D")
                        .withInputs(
                            TEGArtefactDefBuilder("BOutput").build(),
                            TEGArtefactDefBuilder("COutput").build(),
                        )
                        .build(),
                    NOW_0,
                ),
            )
        }

        @Test
        fun `when A completes, both B and C should be scheduled`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))
            sut.givenTheDatesToReturn(NOW_1)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "A",
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                )
            )

            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("B")
                    .asRunType()
                    .withArtefacts(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                    .build(),
                TEGMessageBuilder("C")
                    .asRunType()
                    .withArtefacts(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                    .build(),
            )
        }

        @Test
        fun `when only B completes, D should not be scheduled yet`() {
            val eventsAfterACompleted = baseEvents + listOf(
                TEGEvent.Completed(
                    taskName = "A",
                    timestamp = NOW_1,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                ),
                TEGEvent.Scheduled(taskName = "B", timestamp = NOW_1),
                TEGEvent.Scheduled(taskName = "C", timestamp = NOW_1),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsAfterACompleted))
            sut.givenTheDatesToReturn(NOW_2)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "B",
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "BOutput", value = "result of B")
                    )
                )
            )

            sut.thenTheScheduledTasksAre()
        }

        @Test
        fun `when both B and C complete, D should be scheduled with both artefacts`() {
            val eventsAfterBCompleted = baseEvents + listOf(
                TEGEvent.Completed(
                    taskName = "A",
                    timestamp = NOW_1,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                ),
                TEGEvent.Scheduled(taskName = "B", timestamp = NOW_1),
                TEGEvent.Scheduled(taskName = "C", timestamp = NOW_1),
                TEGEvent.Completed(
                    taskName = "B",
                    timestamp = NOW_2,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "BOutput", value = "result of B")
                    )
                ),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsAfterBCompleted))
            sut.givenTheDatesToReturn(NOW_3)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "C",
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "COutput", value = "result of C")
                    )
                )
            )

            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("D")
                    .asRunType()
                    .withArtefacts(
                        TEGArtefact.TEGArtefactStringValue(name = "BOutput", value = "result of B"),
                        TEGArtefact.TEGArtefactStringValue(name = "COutput", value = "result of C"),
                    )
                    .build(),
            )
        }

        @Test
        fun `diamond pattern completes successfully when D finishes`() {
            val eventsBeforeDCompletes = baseEvents + listOf(
                TEGEvent.Completed(
                    taskName = "A",
                    timestamp = NOW_1,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                ),
                TEGEvent.Scheduled(taskName = "B", timestamp = NOW_1),
                TEGEvent.Scheduled(taskName = "C", timestamp = NOW_1),
                TEGEvent.Completed(
                    taskName = "B",
                    timestamp = NOW_2,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "BOutput", value = "result of B")
                    )
                ),
                TEGEvent.Completed(
                    taskName = "C",
                    timestamp = NOW_2,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "COutput", value = "result of C")
                    )
                ),
                TEGEvent.Scheduled(taskName = "D", timestamp = NOW_2),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsBeforeDCompletes))
            sut.givenTheDatesToReturn(NOW_3)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = "D",
                    outputArtefacts = listOf()
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to eventsBeforeDCompletes + listOf(
                        TEGEvent.Completed(
                            taskName = "D",
                            timestamp = NOW_3,
                            outputArtefacts = listOf()
                        ),
                        TEGEvent.NoMoreTasksToSchedule(timestamp = NOW_3)
                    )
                )
            )
        }
    }

    @Nested
    inner class TegCompletionOnError {
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
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    NOW_0,
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Failed(taskName = "A", timestamp = NOW_1, reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_1),
                TEGEvent.Failed(taskName = "A", timestamp = NOW_2, reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_2),
            )
        }

        @Test
        fun `when a task fails three times, the TEG should be marked as failed`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))
            sut.givenTheDatesToReturn(NOW_3)

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Third failure"
                )
            )

            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to baseEvents + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_3,
                            reason = "Third failure"
                        ),
                        TEGEvent.TEGFailed(
                            timestamp = NOW_3,
                            reason = "Max retries exceeded for task A"
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
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    NOW_0,
                ),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withInputs(
                            TEGArtefactDefinition(
                                name = "AOutput",
                                type = TEGArtefactType.STRING_VALUE
                            )
                        )
                        .build(),
                    NOW_0,
                ),
            )
            sut.givenTheDatesToReturn(NOW_3)
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

            sut.thenTheUpdateResultIsASuccess()
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

            sut.thenTheUpdateResultIsASuccess()
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to baseEvents + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_3,
                            reason = "Worker crashed",
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            timestamp = NOW_3,
                        )
                    )
                )
            )
        }

        @Test
        fun `on third failure for the same task, should abort and return max retries exceeded error`() {
            val eventsWithTwoFailures = baseEvents + listOf(
                TEGEvent.Failed(taskName = "A", timestamp = NOW_1, reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_1),
                TEGEvent.Failed(taskName = "A", timestamp = NOW_2, reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_2),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithTwoFailures))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Third failure"
                )
            )

            sut.thenTheUpdateResultIsAnError(
                TegUpdateError.MaxRetriesExceeded(taskName = "A")
            )
        }

        @Test
        fun `on third failure, the task should not be rescheduled`() {
            val eventsWithTwoFailures = baseEvents + listOf(
                TEGEvent.Failed(taskName = "A", timestamp = NOW_1, reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_1),
                TEGEvent.Failed(taskName = "A", timestamp = NOW_2, reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_2),
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
                TEGEvent.Failed(taskName = "A", timestamp = NOW_1, reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_1),
                TEGEvent.Failed(taskName = "A", timestamp = NOW_2, reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_2),
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
                            timestamp = NOW_3,
                            reason = "Third failure"
                        ),
                        TEGEvent.TEGFailed(
                            timestamp = NOW_3,
                            reason = "Max retries exceeded for task A"
                        )
                    )
                )
            )
        }

        @Test
        fun `on failure of a task with no dependents, no new tasks should be scheduled`() {
            val eventsForTaskWithNoDependents = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .build(),
                    NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    NOW_0,
                ),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsForTaskWithNoDependents))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Worker crashed"
                )
            )

            sut.thenTheUpdateResultIsASuccess()
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build()
            )
        }

        @Test
        fun `on failure for an already completed task, the event should be saved but no rescheduling`() {
            val eventsWithCompletedTask = baseEvents + listOf(
                TEGEvent.Completed(
                    taskName = "A",
                    timestamp = NOW_1,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactStringValue(name = "AOutput", value = "result of A")
                    )
                ),
                TEGEvent.Scheduled(taskName = "B", timestamp = NOW_1),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithCompletedTask))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Late failure message for already completed task"
                )
            )

            sut.thenTheUpdateResultIsASuccess()
            sut.thenTheScheduledTasksAre()
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to eventsWithCompletedTask + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_3,
                            reason = "Late failure message for already completed task"
                        ),
                    )
                )
            )
        }

        @Test
        fun `if a task had failed two times before, completes then fails a third time, that doesn't count as TEGFailure`() {
            sut.givenTheDatesToReturn(NOW_4)
            val eventsWithTwoFailures = baseEvents + listOf(
                TEGEvent.Failed(taskName = "A", timestamp = NOW_1, reason = "First failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_1),
                TEGEvent.Failed(taskName = "A", timestamp = NOW_2, reason = "Second failure"),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_2),
                TEGEvent.Completed(
                    taskName = "A",
                    timestamp = NOW_3,
                    outputArtefacts = listOf()
                ),
                TEGEvent.Scheduled(taskName = "B", timestamp = NOW_3),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithTwoFailures))

            sut.whenGettingTegUpdate(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = "A",
                    reason = "Third failure after completion"
                )
            )

            sut.thenTheUpdateResultIsASuccess()
            sut.thenTheScheduledTasksAre()
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to eventsWithTwoFailures + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_4,
                            reason = "Third failure after completion"
                        ),
                    )
                )
            )
        }
    }

    @Nested
    inner class HandleOtherMessages {
        @BeforeEach
        fun setup() {
            sut.givenTheDatesToReturn(NOW_1)
        }

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
                            timestamp = NOW_1,
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
                            timestamp = NOW_1,
                            log = "This is a log message"
                        )
                    )
                )
            )
        }
    }

    @Nested
    inner class Timeouts {
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
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    timestamp = NOW_0,
                ),
            )
        }

        @Test
        fun `if a task takes too long to complete, it should be rescheduled`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))
            sut.givenTheDatesToReturn(NOW_6)

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheTimeoutCheckResultIsASuccess()
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to baseEvents + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_6,
                            reason = "Task timed out after PT0.005S (started at 1970-01-01T00:00:00Z)",
                        ),
                        TEGEvent.Scheduled(
                            taskName = "A",
                            timestamp = NOW_6,
                        )
                    )
                )
            )
        }

        @Test
        fun `if a task takes too long to complete, it should be marked as failed`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))
            sut.givenTheDatesToReturn(NOW_6)

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheTimeoutCheckResultIsASuccess()
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build()
            )
        }

        @Test
        fun `multiple tasks timing out simultaneously should all be rescheduled`() {
            val eventsWithMultipleTasks = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withOutputs(TEGArtefactDefBuilder("AOutput").build())
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_0),
                TEGEvent.Created(
                    TEGTaskBuilder("B")
                        .withOutputs(TEGArtefactDefBuilder("BOutput").build())
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
                TEGEvent.Scheduled(taskName = "B", timestamp = NOW_0),
                TEGEvent.Created(
                    TEGTaskBuilder("C")
                        .withInputs(
                            TEGArtefactDefBuilder("AOutput").build(),
                            TEGArtefactDefBuilder("BOutput").build(),
                        )
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithMultipleTasks))
            sut.givenTheDatesToReturn(NOW_6)

            sut.whenTheTimeoutCheckerRuns()


            sut.thenTheTimeoutCheckResultIsASuccess()
            sut.thenTheScheduledTasksAre(
                TEGMessageBuilder("A")
                    .asRunType()
                    .build(),
                TEGMessageBuilder("B")
                    .asRunType()
                    .build(),
            )
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to eventsWithMultipleTasks + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = NOW_6,
                            reason = "Task timed out after PT0.005S (started at 1970-01-01T00:00:00Z)",
                        ),
                        TEGEvent.Scheduled(taskName = "A", timestamp = NOW_6),
                        TEGEvent.Failed(
                            taskName = "B",
                            timestamp = NOW_6,
                            reason = "Task timed out after PT0.005S (started at 1970-01-01T00:00:00Z)",
                        ),
                        TEGEvent.Scheduled(taskName = "B", timestamp = NOW_6),
                    )
                )
            )
        }

        @Test
        fun `task should not timeout on exactly the timeout boundary`() {
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))
            sut.givenTheDatesToReturn(NOW_5)

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheTimeoutCheckResultIsASuccess()
            sut.thenTheScheduledTasksAre()
            sut.thenThePersistedEventsShouldBe(mapOf(TEST_TEG_ID to baseEvents))
        }

        @Test
        fun `timeout on third attempt should mark the TEG as failed`() {
            val eventsWithTwoTimeoutFailures = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_0),
                TEGEvent.Failed(
                    taskName = "A",
                    timestamp = NOW_6,
                    reason = "Task timed out after PT0.005S (started at 1970-01-01T00:00:00Z)",
                ),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_6),
                TEGEvent.Failed(
                    taskName = "A",
                    timestamp = NOW_12,
                    reason = "Task timed out after PT0.005S (started at 1970-01-01T00:00:00.006Z)",
                ),
                TEGEvent.Scheduled(taskName = "A", timestamp = NOW_12),
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to eventsWithTwoTimeoutFailures))
            // NOW_12 + 5ms timeout = NOW_17, check at a time after that
            sut.givenTheDatesToReturn(Instant.ofEpochMilli(18))

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheScheduledTasksAre()
            sut.thenThePersistedEventsShouldBe(
                mapOf(
                    TEST_TEG_ID to eventsWithTwoTimeoutFailures + listOf(
                        TEGEvent.Failed(
                            taskName = "A",
                            timestamp = Instant.ofEpochMilli(18),
                            reason = "Task timed out after PT0.005S (started at 1970-01-01T00:00:00.012Z)",
                        ),
                        TEGEvent.TEGFailed(
                            timestamp = Instant.ofEpochMilli(18),
                            reason = "Max retries exceeded for task A"
                        )
                    )
                )
            )
        }
    }

    @Nested
    inner class TimeoutsEdgeCases {
        @BeforeEach
        fun setup() {
            sut.givenTheDatesToReturn(NOW_2)
        }

        @Test
        fun `if a TEG is marked as failed it shouldn't be evaluated for timeouts anymore`() {
            val baseEvents = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    timestamp = NOW_0,
                ),
                TEGEvent.Failed(
                    taskName = "A",
                    timestamp = NOW_1,
                    reason = "Worker crashed"
                ),
                TEGEvent.TEGFailed(
                    timestamp = NOW_1,
                    reason = "Task A failed, so the whole TEG is marked as failed"
                )
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheTimeoutCheckResultIsASuccess()
            sut.thenThePersistedEventsShouldBe(mapOf(TEST_TEG_ID to baseEvents))
        }

        @Test
        fun `if a TEG is marked as no more tasks to schedule it shouldn't be evaluated for timeouts anymore`() {
            val baseEvents = listOf(
                TEGEvent.Created(
                    TEGTaskBuilder("A")
                        .withTimeout(Duration.ofMillis(5))
                        .build(),
                    timestamp = NOW_0,
                ),
                TEGEvent.Scheduled(
                    taskName = "A",
                    timestamp = NOW_0,
                ),
                TEGEvent.Completed(
                    taskName = "A",
                    timestamp = NOW_1,
                    outputArtefacts = listOf(),
                ),
                TEGEvent.NoMoreTasksToSchedule(
                    timestamp = NOW_1,
                )
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheTimeoutCheckResultIsASuccess()
            sut.thenThePersistedEventsShouldBe(mapOf(TEST_TEG_ID to baseEvents))
        }

        @Test
        fun `if a scheduled event doesn't have a corresponding created event, the ScheduledEventWithoutCreatedTask error should be returned`() {
            val baseEvents = listOf(
                TEGEvent.Scheduled(
                    taskName = "A",
                    timestamp = NOW_0,
                )
            )
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to baseEvents))

            sut.whenTheTimeoutCheckerRuns()

            sut.thenTheTimeoutCheckResultIsAnError(
                TegTimeoutCheckError.ScheduledEventWithoutCreatedTask(taskName = "A")
            )
        }
    }
}