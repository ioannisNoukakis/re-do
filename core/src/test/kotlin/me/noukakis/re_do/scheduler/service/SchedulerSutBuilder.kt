package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.scheduler.adapter.SpyMessagingAdapter
import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGMessage
import me.noukakis.re_do.scheduler.model.TEGTask
import org.junit.jupiter.api.Assertions.assertEquals

class SchedulerSutBuilder {
    val messagingAdapter = SpyMessagingAdapter()
    var sut: TEGScheduler? = null

    fun whenSubmittingTheTeg(vararg tasks: TEGTask) {
        if (sut == null) {
            sut = TEGScheduler(messagingAdapter)
        }
        sut!!.scheduleTeg(ScheduleTEGCommand(tasks.toList()))
    }

    fun thenTheScheduledTasksAre(vararg expectedTegMessage: TEGMessage) {
        assertEquals(
            expectedTegMessage.toList(),
            messagingAdapter.sentMessages,
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