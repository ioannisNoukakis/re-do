package me.noukakis.re_do.runner.port

import me.noukakis.re_do.common.port.MessageListenerErrorPort
import me.noukakis.re_do.scheduler.model.TaskRunnerError

interface RunnerErrorHandlerPort : MessageListenerErrorPort {
    fun onTaskExecutionError(error: TaskRunnerError)
}

