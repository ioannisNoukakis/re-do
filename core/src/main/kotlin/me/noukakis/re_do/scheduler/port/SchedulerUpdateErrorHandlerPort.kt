package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.common.port.MessageListenerErrorPort
import me.noukakis.re_do.scheduler.model.TegUpdateError

interface SchedulerUpdateErrorHandlerPort : MessageListenerErrorPort {
    fun onUpdateError(error: TegUpdateError)
}

