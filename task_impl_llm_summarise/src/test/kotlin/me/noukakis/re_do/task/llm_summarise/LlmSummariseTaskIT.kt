package me.noukakis.re_do.task.llm_summarise

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class LlmSummariseTaskIT {
    private lateinit var workingDir: Path
    private lateinit var context: SpyTaskExecutionContext
    private lateinit var sut: LlmSummariseTask
    private lateinit var text: String

    @BeforeEach
    fun setUp() {
        assumeTrue(
            System.getenv("LLM_API_KEY") != null,
            "LLM_API_KEY is not set — skipping integration test",
        )
        workingDir = Files.createTempDirectory("llm-summarise-it-")
        context = SpyTaskExecutionContext(workingDir)
        sut = LlmSummariseTask()
        text = Files.readString(Path.of("src/test/resources/sample_text.txt"))
    }

    @AfterEach
    fun tearDown() {
        if (::workingDir.isInitialized) workingDir.toFile().deleteRecursively()
    }

    @Test
    fun `returns Success when summarising from inline text`() {
        val result = sut.run(
            emptyList(),
            listOf(
                "summary.txt",
                text,
            ),
            context,
        )

        assertTrue(result is TaskImplementationResult.Success)
        println("Generated summary file: ${(result.outputArtefacts[0] as LocalTegArtefact.LocalTegArtefactFile).path.toFile().readText()}")
    }

    @Test
    fun `produces a non-empty summary file`() {
        sut.run(
            emptyList(),
            listOf(
                "summary.txt",
                text,
            ),
            context,
        )

        assertTrue(Files.readString(workingDir.resolve("summary.txt")).isNotBlank())
    }

    @Test
    fun `summarises text supplied via a file artefact`() {
        val inputPath = workingDir.resolve("input.txt")
        Files.writeString(inputPath, text)
        val artefact = LocalTegArtefact.LocalTegArtefactFile("input.txt", inputPath)

        val result = sut.run(listOf(artefact), listOf("summary.txt"), context)

        assertTrue(result is TaskImplementationResult.Success)
    }

    @Test
    fun `summarises text supplied via a string artefact`() {
        val artefact = LocalTegArtefact.LocalTEGArtefactStringValue(
            name = "input",
            value = text,
        )

        val result = sut.run(listOf(artefact), listOf("summary.txt"), context)

        assertTrue(result is TaskImplementationResult.Success)
    }

    @Test
    fun `summarises content concatenated from inline arg, file artefact, and string artefact`() {
        val inputPath = workingDir.resolve("input.txt")
        Files.writeString(inputPath, "The cat sat on the mat. It was a tabby.")
        val fileArtefact = LocalTegArtefact.LocalTegArtefactFile("input.txt", inputPath)
        val stringArtefact = LocalTegArtefact.LocalTEGArtefactStringValue(
            name = "extra",
            value = "The dog watched from the doorway.",
        )

        val result = sut.run(
            listOf(fileArtefact, stringArtefact),
            listOf("summary.txt", "It purred contentedly."),
            context,
        )

        assertTrue(result is TaskImplementationResult.Success)
    }
}
