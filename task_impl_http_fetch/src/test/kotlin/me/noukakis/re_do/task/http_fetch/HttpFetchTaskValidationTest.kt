package me.noukakis.re_do.task.http_fetch

import me.noukakis.re_do.runner.port.TaskImplementationResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class HttpFetchTaskValidationTest {
    private lateinit var sut: HttpFetchTask
    private lateinit var context: SpyTaskExecutionContext

    @BeforeEach
    fun setUp() {
        sut = HttpFetchTask()
        context = SpyTaskExecutionContext(Files.createTempDirectory("http-fetch-validation-"))
    }

    @Test
    fun `implementationName returns correct name`() {
        assertEquals("HttpFetchTask", sut.implementationName())
    }

    @Nested
    inner class `Returns Failure` {
        @Test
        fun `when arguments are empty`() {
            assertEquals(
                TaskImplementationResult.Failure("First argument (url) is required"),
                sut.run(emptyList(), emptyList(), context),
            )
        }

        @Test
        fun `when url is blank`() {
            assertEquals(
                TaskImplementationResult.Failure("First argument (url) must not be blank"),
                sut.run(emptyList(), listOf("   ", "out.bin"), context),
            )
        }

        @Test
        fun `when filename argument is missing`() {
            assertEquals(
                TaskImplementationResult.Failure("Second argument (output filename) is required"),
                sut.run(emptyList(), listOf("https://example.com/file"), context),
            )
        }

        @Test
        fun `when filename argument is blank`() {
            assertEquals(
                TaskImplementationResult.Failure("Second argument (output filename) must not be blank"),
                sut.run(emptyList(), listOf("https://example.com/file", "   "), context),
            )
        }

        @Test
        fun `when header arguments are not in pairs`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Header arguments must be name/value pairs (got 1 after url and filename)",
                ),
                sut.run(emptyList(), listOf("https://example.com/file", "out.bin", "X-Lonely"), context),
            )
        }

        @Test
        fun `when url has file scheme`() {
            assertEquals(
                TaskImplementationResult.Failure("URL scheme must be http or https; got 'file'"),
                sut.run(emptyList(), listOf("file:///etc/passwd", "out.bin"), context),
            )
        }

        @Test
        fun `when url has data scheme`() {
            assertEquals(
                TaskImplementationResult.Failure("URL scheme must be http or https; got 'data'"),
                sut.run(emptyList(), listOf("data:text/plain,hello", "out.bin"), context),
            )
        }

        @Test
        fun `when url has ftp scheme`() {
            assertEquals(
                TaskImplementationResult.Failure("URL scheme must be http or https; got 'ftp'"),
                sut.run(emptyList(), listOf("ftp://example.com/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url has no host`() {
            assertEquals(
                TaskImplementationResult.Failure("URL has no host"),
                sut.run(emptyList(), listOf("http:///path", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to loopback address`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '127.0.0.1' resolves to a private/internal address (127.0.0.1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://127.0.0.1/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to RFC1918 address`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '10.0.0.1' resolves to a private/internal address (10.0.0.1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://10.0.0.1/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to link-local address`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '169.254.1.1' resolves to a private/internal address (169.254.1.1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://169.254.1.1/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to IPv6 loopback address`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '[::1]' resolves to a private/internal address (0:0:0:0:0:0:0:1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://[::1]/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to IPv6 link-local address`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '[fe80::1]' resolves to a private/internal address (fe80:0:0:0:0:0:0:1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://[fe80::1]/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to IPv6 ULA address with fc prefix`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '[fc00::1]' resolves to a private/internal address (fc00:0:0:0:0:0:0:1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://[fc00::1]/file", "out.bin"), context),
            )
        }

        @Test
        fun `when url resolves to IPv6 ULA address with fd prefix`() {
            assertEquals(
                TaskImplementationResult.Failure(
                    "Host '[fd00::1]' resolves to a private/internal address (fd00:0:0:0:0:0:0:1); blocked by default",
                ),
                sut.run(emptyList(), listOf("http://[fd00::1]/file", "out.bin"), context),
            )
        }
    }
}
