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
                    .withOutputs(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                    .build(),
            )

            sut.thenTheResultIsASuccess()
        }

        @Test
        fun `should schedule tasks that can immediately run`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                    .build()
            )

            sut.thenTheScheduledTasksAre(
                TEGMessage(type = TEGMessageType.RUN_TASK, taskName = "A"),
            )
        }

        @Test
        fun `should save the dependency map in persistence`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                    .build(),
            )

            sut.thenTheDependencyMapIsSavedCorrectly(
                mapOf(
                    TEST_TEG_ID to
                            mapOf(
                                TEGDependencyKey(
                                    "A",
                                    listOf()
                                ) to null,
                                TEGDependencyKey(
                                    "B",
                                    listOf(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                                ) to null,
                            )
                )
            )
        }
    }
}