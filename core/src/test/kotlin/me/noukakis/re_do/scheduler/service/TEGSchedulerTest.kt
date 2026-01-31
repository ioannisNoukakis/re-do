package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGArtefactType
import me.noukakis.re_do.scheduler.model.TEGMessage
import me.noukakis.re_do.scheduler.model.TEGMessageType
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
        fun `should schedule tasks that can immediately run`() {
            sut.whenSubmittingTheTeg(
                TEGTaskBuilder("A")
                    .withOutputs(TEGArtefactDefinition(taskName="A", type= TEGArtefactType.STRING_VALUE))
                    .build(),
                TEGTaskBuilder("B")
                    .withInputs(TEGArtefactDefinition(taskName = "A", type = TEGArtefactType.STRING_VALUE))
                    .build()
            )

            sut.thenTheScheduledTasksAre(
                TEGMessage(type = TEGMessageType.RUN_TASK, taskName = "A"),
            )
        }
    }
}