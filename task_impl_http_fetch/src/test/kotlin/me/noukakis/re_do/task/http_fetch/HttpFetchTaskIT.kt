package me.noukakis.re_do.task.http_fetch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskImplementationResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.assertContains
import kotlin.test.assertEquals

class HttpFetchTaskIT {
    private lateinit var server: HttpServer
    private lateinit var workingDir: Path
    private lateinit var context: SpyTaskExecutionContext
    private lateinit var sut: HttpFetchTask
    private val capturedRequests = ConcurrentLinkedQueue<CapturedRequest>()

    private data class CapturedRequest(
        val path: String,
        val method: String,
        val headers: Map<String, List<String>>,
    )

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.start()
        workingDir = Files.createTempDirectory("http-fetch-it-")
        context = SpyTaskExecutionContext(workingDir)
        sut = HttpFetchTask(defaultAllowPrivateIPs = true)
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        workingDir.toFile().deleteRecursively()
        capturedRequests.clear()
    }

    private fun baseUrl(): String = "http://127.0.0.1:${server.address.port}"

    private fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path, { exchange ->
            capturedRequests.add(
                CapturedRequest(
                    path = exchange.requestURI.toString(),
                    method = exchange.requestMethod,
                    headers = exchange.requestHeaders.toMap().mapValues { it.value.toList() },
                ),
            )
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        })
    }

    private fun respond(exchange: HttpExchange, status: Int, body: ByteArray, headers: Map<String, String> = emptyMap()) {
        for ((k, v) in headers) exchange.responseHeaders.add(k, v)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    @Nested
    inner class `Happy path` {
        @Test
        fun `returns Success with single file artefact`() {
            route("/data.bin") { ex -> respond(ex, 200, "hello world".toByteArray()) }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/data.bin",
                    "data.bin",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(
                        LocalTegArtefact.LocalTegArtefactFile(
                            name = "data.bin",
                            path = workingDir.resolve("data.bin"),
                        ),
                    ),
                ),
                result,
            )
        }

        @Test
        fun `output file contains downloaded bytes`() {
            val payload = "the quick brown fox".toByteArray()
            route("/file.txt") { ex -> respond(ex, 200, payload) }

            sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/file.txt",
                    "file.txt",
                ),
                context,
            )

            assertContentEquals(payload, Files.readAllBytes(workingDir.resolve("file.txt")))
        }

        @Test
        fun `reports progress at 0 and 100`() {
            route("/x") { ex -> respond(ex, 200, "x".toByteArray()) }

            sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/x",
                    "x",
                ),
                context,
            )

            assertEquals(
                listOf(0 to STEP_DOWNLOAD, 100 to STEP_DOWNLOAD),
                context.progressCalls.filter { it.first == 0 || it.first == 100 },
            )
        }
    }

    @Nested
    inner class `Non-2xx responses` {
        @Test
        fun `404 returns Failure with status code`() {
            route("/missing") { ex -> respond(ex, 404, "Not found here".toByteArray()) }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/missing",
                    "missing",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Failure("HTTP 404: Not found here"),
                result,
            )
        }

        @Test
        fun `500 returns Failure with status and truncated body`() {
            val body = "X".repeat(FAILURE_BODY_MAX + 500).toByteArray()
            route("/boom") { ex -> respond(ex, 500, body) }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/boom",
                    "boom",
                ),
                context,
            )

            val failure = result as TaskImplementationResult.Failure
            assertTrue(failure.reason.startsWith("HTTP 500: "))
            assertEquals(FAILURE_BODY_MAX, failure.reason.removePrefix("HTTP 500: ").length)
        }
    }

    @Nested
    inner class `Redirects` {
        @Test
        fun `single redirect is followed`() {
            route("/r1") { ex ->
                ex.responseHeaders.add("Location", "/target.bin")
                ex.sendResponseHeaders(302, -1)
            }
            route("/target.bin") { ex -> respond(ex, 200, "redirected".toByteArray()) }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/r1",
                    "target.bin",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Success(
                    listOf(
                        LocalTegArtefact.LocalTegArtefactFile(
                            name = "target.bin",
                            path = workingDir.resolve("target.bin"),
                        ),
                    ),
                ),
                result,
            )
        }

        @Test
        fun `exceeding maximum redirects returns Failure`() {
            for (i in 1..10) {
                route("/r$i") { ex ->
                    ex.responseHeaders.add("Location", "/r${i + 1}")
                    ex.sendResponseHeaders(302, -1)
                }
            }
            val limited = HttpFetchTask(defaultAllowPrivateIPs = true, defaultMaxRedirects = 2)

            val result = limited.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/r1",
                    "r1",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Failure("Exceeded maximum redirects (2)"),
                result,
            )
        }

        @Test
        fun `redirect loop returns Failure`() {
            route("/loop") { ex ->
                ex.responseHeaders.add("Location", "/loop")
                ex.sendResponseHeaders(302, -1)
            }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/loop",
                    "loop",
                ),
                context,
            )

            val failure = result as TaskImplementationResult.Failure
            assertTrue(failure.reason.startsWith("Redirect loop detected")) {
                "Unexpected reason: ${failure.reason}"
            }
        }

        @Test
        fun `redirect to disallowed scheme returns Failure`() {
            route("/r") { ex ->
                ex.responseHeaders.add("Location", "file:///etc/passwd")
                ex.sendResponseHeaders(302, -1)
            }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/r",
                    "r",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Failure("URL scheme must be http or https; got 'file'"),
                result,
            )
        }
    }

    @Nested
    inner class `Size limits` {
        @Test
        fun `Content-Length exceeding limit returns Failure`() {
            val payload = ByteArray(2048) { it.toByte() }
            route("/big") { ex -> respond(ex, 200, payload) }
            val capped = HttpFetchTask(defaultAllowPrivateIPs = true, defaultMaxDownloadBytes = 1024)

            val result = capped.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/big",
                    "big",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Failure(
                    "Content-Length 2048 exceeds maximum allowed size of 1024 bytes",
                ),
                result,
            )
        }

        @Test
        fun `streaming overflow without Content-Length returns Failure`() {
            val payload = ByteArray(20_000) { it.toByte() }
            route("/stream") { ex ->
                ex.sendResponseHeaders(200, 0L)
                ex.responseBody.use { it.write(payload) }
            }
            val capped = HttpFetchTask(defaultAllowPrivateIPs = true, defaultMaxDownloadBytes = 1024)

            val result = capped.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/stream",
                    "stream",
                ),
                context,
            )

            assertEquals(
                TaskImplementationResult.Failure(
                    "Download exceeded maximum allowed size of 1024 bytes",
                ),
                result,
            )
        }
    }

    @Nested
    inner class `Headers and credentials` {
        @Test
        fun `custom request headers are sent`() {
            route("/h") { ex -> respond(ex, 200, byteArrayOf(0)) }

            sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/h",
                    "h",
                    "X-Custom",
                    "abc",
                ),
                context,
            )

            val received = capturedRequests.first { it.path == "/h" }
            assertContains(received.headers.keys.map { it.lowercase() }, "x-custom")
            assertEquals("abc", received.headers.entries.first { it.key.equals("X-Custom", ignoreCase = true) }.value.first())
        }

        @Test
        fun `authorization header value is not in failure reason`() {
            val secret = "Bearer s3cret-token-XYZ"
            route("/auth") { ex -> respond(ex, 401, "Unauthorized".toByteArray()) }

            val result = sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/auth",
                    "auth",
                    "Authorization",
                    secret,
                ),
                context,
            )

            val failure = result as TaskImplementationResult.Failure
            assertFalse(failure.reason.contains("s3cret-token-XYZ")) {
                "Failure reason leaked credentials: ${failure.reason}"
            }
        }

        @Test
        fun `authorization header value is not in log lines`() {
            val secret = "Bearer s3cret-token-XYZ"
            route("/auth") { ex -> respond(ex, 200, byteArrayOf(0)) }

            sut.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/auth",
                    "auth",
                    "Authorization",
                    secret,
                ),
                context,
            )

            assertFalse(context.logCalls.any { it.contains("s3cret-token-XYZ") }) {
                "Log lines leaked credentials: ${context.logCalls}"
            }
        }
    }

    @Nested
    inner class `Timeouts` {
        @Test
        fun `read timeout shorter than server delay returns Failure`() {
            route("/slow") { ex ->
                Thread.sleep(1500)
                respond(ex, 200, byteArrayOf(0))
            }
            val fast = HttpFetchTask(
                defaultAllowPrivateIPs = true,
                defaultReadTimeout = Duration.ofMillis(200),
            )

            val result = fast.run(
                emptyList(),
                listOf(
                    "${baseUrl()}/slow",
                    "slow",
                ),
                context,
            )

            assertTrue(result is TaskImplementationResult.Failure)
        }
    }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
