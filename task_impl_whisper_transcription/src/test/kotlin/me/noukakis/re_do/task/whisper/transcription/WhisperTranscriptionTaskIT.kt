package me.noukakis.re_do.task.whisper.transcription

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import me.noukakis.re_do.task.test_support.SpyTaskExecutionContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val INPUT_NAME = "sample.mp3"
private val SAMPLE_AUDIO_PATH = Path.of("../media_samples/finally_awake.mp3")

class WhisperTranscriptionTaskIT {
    private lateinit var sut: WhisperTranscriptionTask
    private lateinit var workingDir: Path
    private lateinit var context: SpyTaskExecutionContext
    private lateinit var inputPath: Path

    @BeforeEach
    fun setUp() {
        assumeTrue(
            System.getenv("OPENAI_API_KEY") != null,
            "OPENAI_API_KEY is not set — skipping integration test",
        )

        sut = WhisperTranscriptionTask()
        workingDir = Files.createTempDirectory("whisper-it-")
        context = SpyTaskExecutionContext(workingDir)
        inputPath = workingDir.resolve(INPUT_NAME)
        Files.copy(SAMPLE_AUDIO_PATH, inputPath, StandardCopyOption.REPLACE_EXISTING)
    }

    @AfterEach
    fun tearDown() {
        if (::workingDir.isInitialized) {
            workingDir.toFile().deleteRecursively()
        }
    }

    @Nested
    inner class `Successful transcription` {
        private lateinit var artefact: LocalTegArtefact.LocalTegArtefactFile

        @BeforeEach
        fun setUp() {
            artefact = LocalTegArtefact.LocalTegArtefactFile(INPUT_NAME, inputPath)
        }

        @Test
        fun `returns Success with a transcript file artefact`() {
            val result = sut.run(listOf(artefact), listOf("transcript.txt"), context)

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(
                        LocalTegArtefact.LocalTegArtefactFile(
                            "transcript.txt",
                            workingDir.resolve("transcript.txt"),
                        ),
                    ),
                ),
                result,
            )
        }

        @Test
        fun `transcript file exists on disk after transcription`() {
            sut.run(listOf(artefact), listOf("transcript.txt"), context)

            assertTrue(Files.exists(workingDir.resolve("transcript.txt")))
        }

        @Test
        fun `transcript file is non-empty`() {
            sut.run(listOf(artefact), listOf("transcript.txt"), context)

            val value = Files.readString(workingDir.resolve("transcript.txt"))
            assertTrue(value.isNotBlank())
        }

        @Test
        fun `reports progress at 0 and 100`() {
            sut.run(listOf(artefact), listOf("transcript.txt"), context)

            assertTrue(context.progressCalls.any { (progress, _) -> progress == 0 })
            assertTrue(context.progressCalls.any { (progress, _) -> progress == 100 })
        }

        @Test
        fun `emits at least one log line`() {
            sut.run(listOf(artefact), listOf("transcript.txt"), context)

            assertTrue(context.logCalls.isNotEmpty())
        }
    }
}
