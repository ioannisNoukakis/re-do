package me.noukakis.re_do.task.whisper.transcription

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.audio.AudioModel
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult
import java.nio.file.Files

private const val IMPLEMENTATION_NAME = "WhisperTranscriptionTask"

private const val STEP_NAME = "TRANSCRIPTION_OPENAI"

private const val ENV_API_KEY = "OPENAI_API_KEY"
private const val ENV_BASE_URL = "OPENAI_BASE_URL"
private const val ENV_BASE_MODEL = "OPENAI_MODEL"

private const val DEFAULT_MODEL = "whisper-1"

class WhisperTranscriptionTask(
    private val apiKey: String? = System.getenv(ENV_API_KEY),
    private val baseUrl: String? = System.getenv(ENV_BASE_URL),
    private val modelName: String = System.getenv(ENV_BASE_MODEL) ?: DEFAULT_MODEL,
) : TaskHandler {
    private val client: OpenAIClient

    init {
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("$ENV_API_KEY environment variable is not set")
        }


        val clientBuilder = OpenAIOkHttpClient.builder().apiKey(apiKey)
        baseUrl?.let { clientBuilder.baseUrl(it) }
        client = clientBuilder.build()
    }

    override fun implementationName(): String = IMPLEMENTATION_NAME

    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        val fileArtefacts = artefacts.filterIsInstance<LocalTegArtefact.LocalTegArtefactFile>()
        if (fileArtefacts.size != 1) {
            return TaskImplementationResult.Failure(
                "Expected exactly one file artefact, got ${fileArtefacts.size}"
            )
        }
        val fileArtefact = fileArtefacts.single()

        val outputFileName = arguments.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: return TaskImplementationResult.Failure("First argument (output file name) is required")

        val language = arguments.getOrNull(1)?.takeIf { it.isNotBlank() }

        context.reportProgress(0, STEP_NAME)
        context.reportLog("Transcribing ${fileArtefact.name} with model $modelName")

        val paramsBuilder = TranscriptionCreateParams.builder()
            .file(fileArtefact.path)
            .model(AudioModel.of(modelName))
        language?.let { paramsBuilder.language(it) }

        val response = client.audio().transcriptions().create(paramsBuilder.build())
        val text = response.asTranscription()
            .text()

        context.reportProgress(100, STEP_NAME)
        context.reportLog("Transcription complete: ${text.length} characters")

        val outputFile = context.workingDir().resolve(outputFileName)
        Files.writeString(outputFile, text)

        return TaskImplementationResult.Success(
            listOf(
                LocalTegArtefact.LocalTegArtefactFile(
                    name = outputFileName,
                    path = outputFile,
                )
            )
        )
    }
}
