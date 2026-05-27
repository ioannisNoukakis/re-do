package me.noukakis.re_do.task.ffmpeg

import me.noukakis.re_do.common.model.TaskProgress
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val IMPLEMENTATION_NAME = "FFMPEGTask"

class FFMPEGTask : TaskHandler {
    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        if (arguments.size < 2) {
            return TaskImplementationResult.Failure(
                "Expected at least 2 arguments: ffmpeg args string and timeout in seconds",
            )
        }

        val timeoutSeconds = arguments[1].toLongOrNull()
            ?: return TaskImplementationResult.Failure("Invalid timeout value: '${arguments[1]}'")

        val tokens = FfmpegArgumentParser.tokenize(arguments[0])
        val inputFilenames = FfmpegArgumentParser.inputFilenames(tokens)
        val fileInputNames = inputFilenames.filter { FfmpegArgumentParser.isFileReference(it) }.toSet()

        val fileArtefacts = artefacts.filterIsInstance<LocalTegArtefact.LocalTegArtefactFile>()
        val artefactNames = fileArtefacts.map { it.name }.toSet()

        for (name in fileInputNames) {
            if (name !in artefactNames) {
                return TaskImplementationResult.Failure(
                    "Input file '$name' referenced in arguments but not provided as an artefact",
                )
            }
        }
        for (artefact in fileArtefacts) {
            if (artefact.name !in fileInputNames) {
                return TaskImplementationResult.Failure(
                    "Artefact '${artefact.name}' provided but not referenced in ffmpeg arguments",
                )
            }
        }

        val workingDir = context.workingDir()
        for (artefact in fileArtefacts) {
            val link = workingDir.resolve(artefact.name)
            if (!Files.exists(link)) {
                Files.createSymbolicLink(link, artefact.path.toAbsolutePath())
            }
        }

        val command = listOf("ffmpeg", "-y", "-hide_banner") + tokens
        val failure = launchAndWait(command, workingDir, timeoutSeconds, context)
        if (failure != null) return failure

        val outputArtefacts = FfmpegArgumentParser.outputFilenames(tokens).map { filename ->
            LocalTegArtefact.LocalTegArtefactFile(name = filename, path = workingDir.resolve(filename))
        }
        return TaskImplementationResult.Success(outputArtefacts)
    }

    private fun launchAndWait(
        command: List<String>,
        workingDir: Path,
        timeoutSeconds: Long,
        context: TaskExecutionContext,
    ): TaskImplementationResult.Failure? {
        val process = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        val totalDurationSeconds = AtomicReference(0.0)
        val stderrThread = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                context.reportLog(line)
                FfmpegProgressParser.parseDurationSeconds(line)?.let { totalDurationSeconds.set(it) }
                FfmpegProgressParser.parseCurrentSeconds(line)?.let { current ->
                    val total = totalDurationSeconds.get()
                    if (total > 0) {
                        context.reportProgress(
                            TaskProgress.Bounded(
                                step = "encoding",
                                percent = (current / total * 100).toInt().coerceIn(0, 100),
                            ),
                        )
                    }
                }
            }
        }
        stderrThread.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            stderrThread.join()
            return TaskImplementationResult.Failure("FFmpeg process timed out after ${timeoutSeconds}s")
        }
        stderrThread.join()

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            return TaskImplementationResult.Failure("FFmpeg exited with code $exitCode")
        }
        return null
    }

    override fun implementationName(): String = IMPLEMENTATION_NAME
}
