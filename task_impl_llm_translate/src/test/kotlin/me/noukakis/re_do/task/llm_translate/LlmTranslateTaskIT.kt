package me.noukakis.re_do.task.llm_translate

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import me.noukakis.re_do.task.test_support.SpyTaskExecutionContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class LlmTranslateTaskIT {
    private lateinit var workingDir: Path
    private lateinit var context: SpyTaskExecutionContext
    private lateinit var sut: LlmTranslateTask

    @BeforeEach
    fun setUp() {
        assumeTrue(
            System.getenv("LLM_API_KEY") != null,
            "LLM_API_KEY is not set — skipping integration test",
        )
        workingDir = Files.createTempDirectory("llm-translate-it-")
        context = SpyTaskExecutionContext(workingDir)
        sut = LlmTranslateTask()
    }

    @AfterEach
    fun tearDown() {
        if (::workingDir.isInitialized) workingDir.toFile().deleteRecursively()
    }

    @Test
    fun `returns Success when translating a short phrase to French`() {
        val result = sut.run(
            emptyList(),
            listOf("translation.txt", "French", "Hello, how are you today?"),
            context,
        )

        assertTrue(result is TaskImplementationResult.Success)
    }

    @Test
    fun `produces a non-empty translation file`() {
        sut.run(
            emptyList(),
            listOf("translation.txt", "French", "Hello, how are you today?"),
            context,
        )

        assertTrue(Files.readString(workingDir.resolve("translation.txt")).isNotBlank())
    }

    @Test
    fun `Translates from a string artefact`() {
        val artefact = LocalTegArtefact.LocalTEGArtefactStringValue(
            name = "input",
            value = "Hello, how are you today?",
        )

        val result = sut.run(listOf(artefact), listOf("translation.txt", "French"), context)

        assertTrue(result is TaskImplementationResult.Success)
    }

    @Test
    fun `Translates from a file artefact`() {
        val inputPath = workingDir.resolve("input.txt")
        Files.writeString(inputPath, "Hello, how are you today?")
        val artefact = LocalTegArtefact.LocalTegArtefactFile("input.txt", inputPath)

        val result = sut.run(listOf(artefact), listOf("translation.txt", "French"), context)

        assertTrue(result is TaskImplementationResult.Success)
    }

    @Test
    fun `Translates from both string, file artefacts and third argument, concatenating them`() {
        val inputPath = workingDir.resolve("input.txt")
        Files.writeString(inputPath, "How is the weather today?")
        val fileArtefact = LocalTegArtefact.LocalTegArtefactFile("input.txt", inputPath)
        val stringArtefact = LocalTegArtefact.LocalTEGArtefactStringValue(
            name = "extra",
            value = "Where are you going?",
        )

        val result = sut.run(
            listOf(fileArtefact, stringArtefact),
            listOf("translation.txt", "French", "Hello, how are you today?"),
            context,
        )

        assertTrue(result is TaskImplementationResult.Success)
        println("Generated translation file: ${(result.outputArtefacts[0] as LocalTegArtefact.LocalTegArtefactFile).path.toFile().readText()}")
    }
}
