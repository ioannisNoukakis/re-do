package me.noukakis.re_do.task.ffmpeg

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val INPUT_NAME = "input.mp4"

class FFMPEGTaskIT {
    private lateinit var sut: FFMPEGTask
    private lateinit var workingDir: Path
    private lateinit var context: SpyTaskExecutionContext
    private lateinit var inputPath: Path

    @BeforeEach
    fun setUp() {
        sut = FFMPEGTask()
        workingDir = Files.createTempDirectory("ffmpeg-it-working-dir")
        context = SpyTaskExecutionContext(workingDir)
        inputPath = workingDir.resolve(INPUT_NAME)
        Files.copy(Path.of("../media_samples/finally_awake_1080p.mp4"), inputPath, StandardCopyOption.REPLACE_EXISTING)
    }

    @AfterEach
    fun tearDown() {
        workingDir.toFile().deleteRecursively()
    }

    @Nested
    inner class `Successful conversion` {
        private lateinit var artefact: LocalTegArtefact

        @BeforeEach
        fun setUp() {
            artefact = LocalTegArtefact.LocalTegArtefactFile(INPUT_NAME, inputPath)
        }

        @Test
        fun `returns Success with the correct output artefact`() {
            val result = sut.run(listOf(artefact), listOf("-i $INPUT_NAME -ar 16000 output.wav", "30"), context)

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(LocalTegArtefact.LocalTegArtefactFile("output.wav", workingDir.resolve("output.wav"))),
                ),
                result,
            )
        }

        @Test
        fun `output file exists on disk after conversion`() {
            sut.run(listOf(artefact), listOf("-i $INPUT_NAME -ar 16000 output.wav", "30"), context)

            assertTrue(Files.exists(workingDir.resolve("output.wav")))
        }

        @Test
        fun `forwards stderr log lines via reportLog`() {
            sut.run(listOf(artefact), listOf("-i $INPUT_NAME -ar 16000 output.wav", "30"), context)

            assertTrue(context.logCalls.isNotEmpty())
        }
    }

    @Nested
    inner class `Failure cases` {
        private lateinit var corruptPath: Path
        private val corruptInputName = "corrupt.mp4"

        @BeforeEach
        fun setUp() {
            corruptPath = workingDir.resolve(corruptInputName)
            Files.write(corruptPath, ByteArray(16) { 0 })
        }

        @Test
        fun `returns Failure when ffmpeg exits with non-zero`() {
            val artefact = LocalTegArtefact.LocalTegArtefactFile(corruptInputName, corruptPath)

            val result = sut.run(listOf(artefact), listOf("-i $corruptInputName output.wav", "30"), context)

            assertTrue(result is TaskImplementationResult.Failure)
        }

        @Test
        fun `returns Failure with exit code message when ffmpeg exits with non-zero`() {
            val artefact = LocalTegArtefact.LocalTegArtefactFile(corruptInputName, corruptPath)

            val result = sut.run(listOf(artefact), listOf("-i $corruptInputName output.wav", "30"), context)

            assertTrue((result as TaskImplementationResult.Failure).reason.contains("FFmpeg exited with code"))
        }

        @Test
        fun `returns Failure when timeout is exceeded`() {
            val result = sut.run(
                emptyList(),
                listOf("-re -f lavfi -i anullsrc=r=44100 -t 999999 -f null -", "1"),
                context,
            )

            assertTrue(result is TaskImplementationResult.Failure)
        }

        @Test
        fun `returns Failure with timed out message when timeout is exceeded`() {
            val result = sut.run(
                emptyList(),
                listOf("-re -f lavfi -i anullsrc=r=44100 -t 999999 -f null -", "2"),
                context,
            )

            assertTrue((result as TaskImplementationResult.Failure).reason.contains("timed out"))
        }
    }
}
