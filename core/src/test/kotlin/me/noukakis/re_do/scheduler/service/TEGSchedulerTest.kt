package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.scheduler.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
                    .withOutputs(TEGArtefactDefinition(name = "AOutput", type = TEGArtefactType.STRING_VALUE))
                    .build(),
            )

            sut.thenTheResultIsASuccess()
        }

        @Test
        fun `should schedule tasks that can immediately run`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefinition(name = "AOutput", type = TEGArtefactType.STRING_VALUE))
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefinition(name = "AOutput", type = TEGArtefactType.STRING_VALUE))
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
                    .withOutputs(TEGArtefactDefinition(name = "AOutput", type = TEGArtefactType.STRING_VALUE))
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefinition(name = "AOutput", type = TEGArtefactType.STRING_VALUE))
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

        // TODO detect cycles in TEG and error out
        // TODO detect missing artefact producers in TEG and error out
    }

    @Nested
    inner class HandleWorkerResultMessage {
        lateinit var list: List<TEGEvent>

        @BeforeEach
        fun setup() {
            list = listOf(
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
            sut.givenTheExistingEvents(mapOf(TEST_TEG_ID to list))

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
                    TEST_TEG_ID to list + listOf(
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
    // TODO: on progress from workers: update persistence
    // TODO: on failure from workers: reschedule or if at max retries fail TEG
}