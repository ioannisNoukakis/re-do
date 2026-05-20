package me.noukakis.re_do.task.test_support

import me.noukakis.re_do.runner.port.TaskExecutionContext
import java.nio.file.Path

class SpyTaskExecutionContext(private val workingDir: Path) : TaskExecutionContext {
    val progressCalls: MutableList<Pair<Int, String>> = mutableListOf()
    val logCalls: MutableList<String> = mutableListOf()

    override fun reportProgress(progress: Int, step: String) {
        progressCalls += Pair(progress, step)
    }

    override fun reportLog(log: String) {
        logCalls += log
    }

    override fun workingDir(): Path = workingDir
}
