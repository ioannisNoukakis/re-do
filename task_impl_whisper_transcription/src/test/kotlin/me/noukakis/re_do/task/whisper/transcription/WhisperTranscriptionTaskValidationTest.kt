package me.noukakis.re_do.task.whisper.transcription

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.test.assertEquals

class WhisperTranscriptionTaskValidationTest {
    private lateinit var context: SpyTaskExecutionContext

    @BeforeEach
    fun setUp() {
        context = SpyTaskExecutionContext(Files.createTempDirectory("openai-validation-test-"))
    }

    private fun audioArtefact(name: String = "audio.mp3") = LocalTegArtefact.LocalTegArtefactFile(name = name, path = context.workingDir().resolve(name))

    @Test
    fun `implementationName returns correct name`() {
        assertEquals("WhisperTranscriptionTask", buildSut().implementationName())
    }

    private fun buildSut(apiKey: String = "test-key"): WhisperTranscriptionTask = WhisperTranscriptionTask(
        apiKey = apiKey,
        baseUrl = "https://some.url",
        modelName = "some-model",
    )

    @Nested
    inner class `Returns Failure` {
        @Test
        fun `when no artefacts provided`() {
            assertEquals(
                TaskImplementationResult.Failure("Expected exactly one file artefact, got 0"),
                buildSut().run(emptyList(), listOf("output.txt"), context),
            )
        }

        @Test
        fun `when more than one file artefact provided`() {
            assertEquals(
                TaskImplementationResult.Failure("Expected exactly one file artefact, got 2"),
                buildSut().run(
                    listOf(audioArtefact("a.mp3"), audioArtefact("b.mp3")),
                    listOf("output.txt"),
                    context,
                ),
            )
        }

        @Test
        fun `when only string artefacts provided`() {
            val stringArtefact = LocalTegArtefact.LocalTEGArtefactStringValue(name = "text", value = "hello")
            assertEquals(
                TaskImplementationResult.Failure("Expected exactly one file artefact, got 0"),
                buildSut().run(listOf(stringArtefact), listOf("output.txt"), context),
            )
        }

        @Test
        fun `when output file argument is missing`() {
            assertEquals(
                TaskImplementationResult.Failure("First argument (output file name) is required"),
                buildSut().run(listOf(audioArtefact()), emptyList(), context),
            )
        }

        @Test
        fun `when output file argument is blank`() {
            assertEquals(
                TaskImplementationResult.Failure("First argument (output file name) is required"),
                buildSut().run(listOf(audioArtefact()), listOf("   "), context),
            )
        }

        @Test
        fun `when API key is blank`() {
            assertThrows<IllegalArgumentException> { buildSut(apiKey = "") }
        }
    }
}
