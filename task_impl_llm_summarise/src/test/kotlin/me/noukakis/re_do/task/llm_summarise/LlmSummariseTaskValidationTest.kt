package me.noukakis.re_do.task.llm_summarise

import me.noukakis.re_do.llm_inference.LlmRequest
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import me.noukakis.re_do.task.test_support.SpyTaskExecutionContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class LlmSummariseTaskValidationTest {
    private lateinit var workingDir: Path
    private lateinit var context: SpyTaskExecutionContext
    private lateinit var fakeClient: FakeLlmInferenceClient
    private lateinit var sut: LlmSummariseTask

    @BeforeEach
    fun setUp() {
        workingDir = Files.createTempDirectory("llm-summarise-validation-")
        context = SpyTaskExecutionContext(workingDir)
        fakeClient = FakeLlmInferenceClient()
        sut = LlmSummariseTask(inferenceClient = fakeClient)
    }

    private fun stringArtefact(name: String, value: String): LocalTegArtefact = LocalTegArtefact.LocalTEGArtefactStringValue(name = name, value = value)

    private fun fileArtefact(name: String, contents: String): LocalTegArtefact {
        val path = workingDir.resolve(name)
        Files.writeString(path, contents)
        return LocalTegArtefact.LocalTegArtefactFile(name = name, path = path)
    }

    @Test
    fun `implementationName returns correct name`() {
        assertEquals("LlmSummariseTask", sut.implementationName())
    }

    @Nested
    inner class `Returns Failure` {

        @Test
        fun `when output filename argument is missing`() {
            assertEquals(
                TaskImplementationResult.Failure("First argument (output file name) is required"),
                sut.run(emptyList(), emptyList(), context),
            )
        }

        @Test
        fun `when output filename argument is blank`() {
            assertEquals(
                TaskImplementationResult.Failure("First argument (output file name) is required"),
                sut.run(emptyList(), listOf("   "), context),
            )
        }

        @Test
        fun `when no inline text and no artefact are provided`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "No content to summarise (no inline text argument and no non-empty input artefact provided)",
                ),
                sut.run(emptyList(), listOf("out.txt"), context),
            )
        }

        @Test
        fun `when only inline text is blank and no artefact`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "No content to summarise (no inline text argument and no non-empty input artefact provided)",
                ),
                sut.run(emptyList(), listOf("out.txt", "   "), context),
            )
        }

        @Test
        fun `when artefacts contain only empty values`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "No content to summarise (no inline text argument and no non-empty input artefact provided)",
                ),
                sut.run(
                    listOf(stringArtefact("a", "   "), fileArtefact("b.txt", "")),
                    listOf("out.txt"),
                    context,
                ),
            )
        }
    }

    @Nested
    inner class `Returns Success` {

        @Test
        fun `when inline text is provided`() {
            val result = sut.run(emptyList(), listOf("out.txt", "hello world"), context)

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(
                        LocalTegArtefact.LocalTegArtefactFile(
                            name = "out.txt",
                            path = workingDir.resolve("out.txt"),
                        ),
                    ),
                ),
                result,
            )
        }

        @Test
        fun `when file artefact is provided`() {
            val result = sut.run(
                listOf(fileArtefact("in.txt", "some long text to summarise")),
                listOf("out.txt"),
                context,
            )

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(
                        LocalTegArtefact.LocalTegArtefactFile(
                            name = "out.txt",
                            path = workingDir.resolve("out.txt"),
                        ),
                    ),
                ),
                result,
            )
        }

        @Test
        fun `when only a string artefact provides content`() {
            val result = sut.run(
                listOf(stringArtefact("text", "some long text to summarise")),
                listOf("out.txt"),
                context,
            )

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(
                        LocalTegArtefact.LocalTegArtefactFile(
                            name = "out.txt",
                            path = workingDir.resolve("out.txt"),
                        ),
                    ),
                ),
                result,
            )
        }
    }

    @Nested
    inner class `Inference request shape` {

        @Test
        fun `concatenates inline text then artefacts in their provided order separated by blank lines`() {
            sut.run(
                listOf(
                    fileArtefact("a.txt", "FILE_A"),
                    stringArtefact("s", "STRING"),
                    fileArtefact("b.txt", "FILE_B"),
                ),
                listOf("out.txt", "ARG"),
                context,
            )

            assertEquals(
                "ARG\n\nFILE_A\n\nSTRING\n\nFILE_B",
                fakeClient.calls.single().userContent,
            )
        }

        @Test
        fun `concatenates multiple string artefacts`() {
            sut.run(
                listOf(stringArtefact("a", "ALPHA"), stringArtefact("b", "BETA")),
                listOf("out.txt"),
                context,
            )

            assertEquals("ALPHA\n\nBETA", fakeClient.calls.single().userContent)
        }

        @Test
        fun `concatenates multiple file artefacts`() {
            sut.run(
                listOf(fileArtefact("a.txt", "ALPHA"), fileArtefact("b.txt", "BETA")),
                listOf("out.txt"),
                context,
            )

            assertEquals("ALPHA\n\nBETA", fakeClient.calls.single().userContent)
        }

        @Test
        fun `skips empty artefacts when concatenating`() {
            sut.run(
                listOf(
                    stringArtefact("a", "ALPHA"),
                    stringArtefact("empty", "   "),
                    fileArtefact("b.txt", ""),
                    fileArtefact("c.txt", "GAMMA"),
                ),
                listOf("out.txt"),
                context,
            )

            assertEquals("ALPHA\n\nGAMMA", fakeClient.calls.single().userContent)
        }

        @Test
        fun `forwards the default system prompt`() {
            sut.run(emptyList(), listOf("out.txt", "anything"), context)

            assertEquals(DEFAULT_SYSTEM_PROMPT, fakeClient.calls.single().systemPrompt)
        }

        @Test
        fun `forwards the default model name`() {
            sut.run(emptyList(), listOf("out.txt", "anything"), context)

            assertEquals(DEFAULT_MODEL, fakeClient.calls.single().model)
        }

        @Test
        fun `writes response content to the output file`() {
            sut.run(emptyList(), listOf("summary.txt", "anything"), context)

            assertEquals("SUMMARY", Files.readString(workingDir.resolve("summary.txt")))
        }

        @Test
        fun `applies all constructor overrides to the inference request`() {
            val customSut = LlmSummariseTask(
                inferenceClient = fakeClient,
                systemPrompt = "Custom system prompt.",
                model = "custom-model",
                maxTokens = 256,
                temperature = 0.7,
                contextWindowTokens = 8_000,
                timeoutSeconds = 45,
            )

            customSut.run(emptyList(), listOf("out.txt", "hello"), context)

            assertEquals(
                LlmRequest(
                    systemPrompt = "Custom system prompt.",
                    userContent = "hello",
                    model = "custom-model",
                    maxTokens = 256,
                    temperature = 0.7,
                    timeoutSeconds = 45,
                    contextWindowTokens = 8_000,
                ),
                fakeClient.calls.single(),
            )
        }
    }
}
