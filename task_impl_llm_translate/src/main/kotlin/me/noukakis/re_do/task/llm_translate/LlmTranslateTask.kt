package me.noukakis.re_do.task.llm_translate

import me.noukakis.re_do.llm_inference.ChunkingLlmInferenceClient
import me.noukakis.re_do.llm_inference.LangChain4jOpenAiLlmBackendAdapter
import me.noukakis.re_do.llm_inference.LlmInferenceClient
import me.noukakis.re_do.llm_inference.LlmRequest
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files

private const val IMPLEMENTATION_NAME = "LlmTranslateTask"
private const val STEP_NAME = "TRANSLATING"

private const val ENV_SYSTEM_PROMPT_TEMPLATE = "LLM_TRANSLATE_SYSTEM_PROMPT_TEMPLATE"
private const val ENV_MODEL = "LLM_TRANSLATE_MODEL"
private const val ENV_MAX_TOKENS = "LLM_TRANSLATE_MAX_TOKENS"
private const val ENV_TEMPERATURE = "LLM_TRANSLATE_TEMPERATURE"
private const val ENV_CONTEXT_WINDOW_TOKENS = "LLM_TRANSLATE_CONTEXT_WINDOW_TOKENS"
private const val ENV_TIMEOUT_SECONDS = "LLM_TRANSLATE_TIMEOUT_SECONDS"

internal const val DEFAULT_SYSTEM_PROMPT_TEMPLATE =
    "You are a professional translator. Translate the user's text into %s. Preserve meaning, tone, and formatting. Output only the translation, with no preamble or commentary."
internal const val DEFAULT_MODEL = "gpt-4o-mini"
internal const val DEFAULT_MAX_TOKENS = 2048
internal const val DEFAULT_TEMPERATURE = 0.2
internal const val DEFAULT_CONTEXT_WINDOW_TOKENS = 128_000
internal const val DEFAULT_TIMEOUT_SECONDS = 120

internal val LANGUAGE_PATTERN = Regex("^[A-Za-z][A-Za-z \\-]{0,39}$")

private fun envInt(name: String, default: Int): Int {
    val raw = System.getenv(name) ?: return default
    return raw.toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer; got '$raw'")
}

private fun envDouble(name: String, default: Double): Double {
    val raw = System.getenv(name) ?: return default
    return raw.toDoubleOrNull() ?: throw IllegalArgumentException("$name must be a number; got '$raw'")
}

class LlmTranslateTask(
    private val inferenceClient: LlmInferenceClient = ChunkingLlmInferenceClient(LangChain4jOpenAiLlmBackendAdapter()),
    private val systemPromptTemplate: String = System.getenv(ENV_SYSTEM_PROMPT_TEMPLATE) ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE,
    private val model: String = System.getenv(ENV_MODEL) ?: DEFAULT_MODEL,
    private val maxTokens: Int = envInt(ENV_MAX_TOKENS, DEFAULT_MAX_TOKENS),
    private val temperature: Double = envDouble(ENV_TEMPERATURE, DEFAULT_TEMPERATURE),
    private val contextWindowTokens: Int = envInt(ENV_CONTEXT_WINDOW_TOKENS, DEFAULT_CONTEXT_WINDOW_TOKENS),
    private val timeoutSeconds: Int = envInt(ENV_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS),
) : TaskHandler {

    override fun implementationName(): String = IMPLEMENTATION_NAME

    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        val outputFileName = arguments.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: return TaskImplementationResult.Failure("First argument (output file name) is required")

        val targetLanguage = arguments.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: return TaskImplementationResult.Failure("Second argument (target language) is required")
        if (!LANGUAGE_PATTERN.matches(targetLanguage)) {
            return TaskImplementationResult.Failure(
                "Second argument (target language) must match $LANGUAGE_PATTERN; got '$targetLanguage'",
            )
        }

        val inlineText = arguments.getOrNull(2).orEmpty()

        val artefactTexts = artefacts.map { artefact ->
            when (artefact) {
                is LocalTegArtefact.LocalTegArtefactFile -> Files.readString(artefact.path, StandardCharsets.UTF_8)
                is LocalTegArtefact.LocalTEGArtefactStringValue -> artefact.value
            }
        }

        val userContent = (listOf(inlineText) + artefactTexts)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        if (userContent.isEmpty()) {
            return TaskImplementationResult.Failure(
                "No content to translate (no inline text argument and no non-empty input artefact provided)",
            )
        }

        val systemPrompt = systemPromptTemplate.format(targetLanguage)

        context.reportProgress(0, STEP_NAME)
        context.reportLog("Translating ${userContent.length} chars into $targetLanguage with model $model")

        val response = inferenceClient.execute(
            LlmRequest(
                systemPrompt = systemPrompt,
                userContent = userContent,
                model = model,
                maxTokens = maxTokens,
                temperature = temperature,
                timeoutSeconds = timeoutSeconds,
                contextWindowTokens = contextWindowTokens,
            ),
        )

        val outputPath = context.workingDir().resolve(outputFileName)
        Files.writeString(outputPath, response.content, StandardCharsets.UTF_8)

        context.reportProgress(100, STEP_NAME)
        context.reportLog(
            "Translation written to $outputFileName (${response.completionTokens} completion tokens, finishReason=${response.finishReason})",
        )

        return TaskImplementationResult.Success(
            listOf(LocalTegArtefact.LocalTegArtefactFile(name = outputFileName, path = outputPath)),
        )
    }
}
