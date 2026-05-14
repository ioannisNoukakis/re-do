package me.noukakis.re_do.task.ffmpeg

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class FFMPEGTaskValidationTest {
    private lateinit var sut: FFMPEGTask
    private lateinit var context: SpyTaskExecutionContext

    @BeforeEach
    fun setUp() {
        sut = FFMPEGTask()
        context = SpyTaskExecutionContext(Files.createTempDirectory("ffmpeg-validation-test-"))
    }

    @Nested
    inner class `Returns Failure` {
        @Test
        fun `when arguments list is empty`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Expected at least 2 arguments: ffmpeg args string and timeout in seconds",
                ),
                sut.run(emptyList(), emptyList(), context),
            )
        }

        @Test
        fun `when timeout argument is not a number`() {
            assertEquals(
                TaskImplementationResult.Failure("Invalid timeout value: 'notanumber'"),
                sut.run(emptyList(), listOf("-i input.wav output.wav", "notanumber"), context),
            )
        }

        @Test
        fun `when input file referenced in args has no matching artefact`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Input file 'input.wav' referenced in arguments but not provided as an artefact",
                ),
                sut.run(emptyList(), listOf("-i input.wav output.wav", "10"), context),
            )
        }

        @Test
        fun `when artefact is not referenced in ffmpeg args`() {
            val artefact = LocalTegArtefact.LocalTegArtefactFile(
                name = "extra.wav",
                path = context.workingDir().resolve("extra.wav"),
            )
            assertEquals(
                TaskImplementationResult.Failure(
                    "Artefact 'extra.wav' provided but not referenced in ffmpeg arguments",
                ),
                sut.run(listOf(artefact), listOf("-f null -", "10"), context),
            )
        }
    }
}
