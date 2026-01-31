package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.scheduler.model.TEGTask
import me.noukakis.re_do.scheduler.port.MessagingPort

data class ScheduleTEGCommand(
    val tasks: List<TEGTask>
)

class TEGScheduler(
    private val messagingPort: MessagingPort
) {
    fun scheduleTeg(command: ScheduleTEGCommand) {
        command.tasks
            .filter { it.inputs.isEmpty() }
            .forEach { messagingPort.send(it.toRunTaskMessage()) }
    }
}