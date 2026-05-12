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

    private fun audioArtefact(name: String = "audio.mp3") =
        LocalTegArtefact.LocalTegArtefactFile(name = name, path = context.workingDir().resolve(name))

    @Test
    fun `implementationName returns correct name`() {
        assertEquals("OpenAITranscriptionTask", WhisperTranscriptionTask(apiKey = "test-key").implementationName())
    }

    @Nested
    inner class `Returns Failure` {
        @Test
        fun `when no artefacts provided`() {
            assertEquals(
                TaskImplementationResult.Failure("Expected exactly one file artefact, got 0"),
                WhisperTranscriptionTask(apiKey = "test-key").run(emptyList(), emptyList(), context),
            )
        }

        @Test
        fun `when more than one file artefact provided`() {
            assertEquals(
                TaskImplementationResult.Failure("Expected exactly one file artefact, got 2"),
                WhisperTranscriptionTask(apiKey = "test-key").run(
                    listOf(audioArtefact("a.mp3"), audioArtefact("b.mp3")),
                    emptyList(),
                    context,
                ),
            )
        }

        @Test
        fun `when only string artefacts provided`() {
            val stringArtefact = LocalTegArtefact.LocalTEGArtefactStringValue(name = "text", value = "hello")
            assertEquals(
                TaskImplementationResult.Failure("Expected exactly one file artefact, got 0"),
                WhisperTranscriptionTask(apiKey = "test-key").run(listOf(stringArtefact), emptyList(), context),
            )
        }

        @Test
        fun `when API key is blank`() {
            assertThrows<IllegalArgumentException> { WhisperTranscriptionTask(apiKey = "") }
        }
    }
}
