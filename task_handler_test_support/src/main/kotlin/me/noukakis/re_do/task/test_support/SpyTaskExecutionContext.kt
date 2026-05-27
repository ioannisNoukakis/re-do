package me.noukakis.re_do.task.test_support

import me.noukakis.re_do.common.model.TaskProgress
import me.noukakis.re_do.runner.port.TaskExecutionContext
import java.nio.file.Path

class SpyTaskExecutionContext(private val workingDir: Path) : TaskExecutionContext {
    val progressCalls: MutableList<TaskProgress> = mutableListOf()
    val logCalls: MutableList<String> = mutableListOf()

    override fun reportProgress(progress: TaskProgress) {
        progressCalls += progress
    }

    override fun reportLog(log: String) {
        logCalls += log
    }

    override fun workingDir(): Path = workingDir
}
