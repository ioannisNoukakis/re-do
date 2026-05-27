package me.noukakis.re_do.task.http_fetch

import me.noukakis.re_do.common.model.TaskProgress
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Locale

internal const val IMPLEMENTATION_NAME = "HttpFetchTask"

internal const val STEP_DOWNLOAD = "DOWNLOADING"
internal const val FAILURE_BODY_MAX = 1024
internal const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024
internal const val DEFAULT_MAX_REDIRECTS = 5
internal val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
internal val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(60)

private class DownloadTooLargeException(message: String) : RuntimeException(message)

class HttpFetchTask(
    private val defaultConnectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    private val defaultReadTimeout: Duration = DEFAULT_READ_TIMEOUT,
    private val defaultMaxDownloadBytes: Long = DEFAULT_MAX_BYTES,
    private val defaultMaxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    private val defaultAllowPrivateIPs: Boolean = false,
) : TaskHandler {

    override fun implementationName(): String = IMPLEMENTATION_NAME

    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        val rawUrl = arguments.getOrNull(0)
            ?: return TaskImplementationResult.Failure("First argument (url) is required")
        if (rawUrl.isBlank()) {
            return TaskImplementationResult.Failure("First argument (url) must not be blank")
        }

        val filename = arguments.getOrNull(1)
            ?: return TaskImplementationResult.Failure("Second argument (output filename) is required")
        if (filename.isBlank()) {
            return TaskImplementationResult.Failure("Second argument (output filename) must not be blank")
        }

        val headerPairs = arguments.drop(2)
        if (headerPairs.size % 2 != 0) {
            return TaskImplementationResult.Failure(
                "Header arguments must be name/value pairs (got ${headerPairs.size} after url and filename)",
            )
        }
        val headers = headerPairs.chunked(2).associate { (name, value) -> name to value }

        val initialUri = try {
            URI(rawUrl.trim())
        } catch (_: URISyntaxException) {
            return TaskImplementationResult.Failure("Invalid URL syntax")
        }
        validateSchemeAndHost(initialUri)?.let { return it }
        rejectPrivateHost(initialUri, defaultAllowPrivateIPs)?.let { return it }

        val client = HttpClient.newBuilder()
            .connectTimeout(defaultConnectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

        return try {
            performFetch(
                client = client,
                initialUri = initialUri,
                headers = headers,
                filename = filename,
                context = context,
            )
        } catch (e: DownloadTooLargeException) {
            TaskImplementationResult.Failure(e.message ?: "Download exceeded maximum size")
        } catch (e: IOException) {
            TaskImplementationResult.Failure("HTTP request failed: ${e.javaClass.simpleName}")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            TaskImplementationResult.Failure("HTTP request interrupted")
        }
    }

    private fun performFetch(
        client: HttpClient,
        initialUri: URI,
        headers: Map<String, String>,
        filename: String,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        val visited = linkedSetOf<URI>()
        var currentUri = initialUri
        var redirectCount = 0
        while (true) {
            if (!visited.add(currentUri)) {
                return TaskImplementationResult.Failure(
                    "Redirect loop detected at ${currentUri.maskUserInfo()}",
                )
            }
            val request = buildRequest(currentUri, headers, defaultReadTimeout)
            context.reportProgress(TaskProgress.Bounded(step = STEP_DOWNLOAD, percent = 0))
            context.reportLog("Fetching ${currentUri.maskUserInfo()}")
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val status = response.statusCode()
            if (status in 300..399) {
                val location = response.headers().firstValue("Location").orElse(null)
                response.body().use { it.drain() }
                if (location.isNullOrBlank()) {
                    return TaskImplementationResult.Failure("Redirect $status without Location header")
                }
                if (redirectCount >= defaultMaxRedirects) {
                    return TaskImplementationResult.Failure(
                        "Exceeded maximum redirects ($defaultMaxRedirects)",
                    )
                }
                val next = try {
                    currentUri.resolve(location)
                } catch (_: IllegalArgumentException) {
                    return TaskImplementationResult.Failure("Invalid redirect Location header")
                }
                validateSchemeAndHost(next)?.let { return it }
                rejectPrivateHost(next, defaultAllowPrivateIPs)?.let { return it }
                currentUri = next
                redirectCount++
                continue
            }
            return handleTerminalResponse(response, filename, context)
        }
    }

    private fun handleTerminalResponse(
        response: HttpResponse<InputStream>,
        filename: String,
        context: TaskExecutionContext,
    ): TaskImplementationResult {
        val status = response.statusCode()
        if (status !in 200..299) {
            val body = response.body().use { readBodyForFailure(it) }
            return TaskImplementationResult.Failure("HTTP $status: $body")
        }

        val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        if (contentLength in 1..Long.MAX_VALUE && contentLength > defaultMaxDownloadBytes) {
            response.body().use { it.drain() }
            return TaskImplementationResult.Failure(
                "Content-Length $contentLength exceeds maximum allowed size of $defaultMaxDownloadBytes bytes",
            )
        }

        val outputPath = context.workingDir().resolve(filename)
        val bytesWritten = response.body().use { stream ->
            streamToFile(stream, outputPath, defaultMaxDownloadBytes, contentLength, context)
        }
        context.reportProgress(TaskProgress.Bounded(step = STEP_DOWNLOAD, percent = 100))
        context.reportLog("Downloaded $bytesWritten bytes to $filename")
        return TaskImplementationResult.Success(
            listOf(LocalTegArtefact.LocalTegArtefactFile(name = filename, path = outputPath)),
        )
    }

    private fun streamToFile(
        stream: InputStream,
        target: Path,
        maxBytes: Long,
        contentLength: Long,
        context: TaskExecutionContext,
    ): Long {
        Files.deleteIfExists(target)
        var written = 0L
        var lastReported = 0
        try {
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { out ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n == -1) break
                    if (written + n > maxBytes) {
                        out.flush()
                        Files.deleteIfExists(target)
                        throw DownloadTooLargeException(
                            "Download exceeded maximum allowed size of $maxBytes bytes",
                        )
                    }
                    out.write(buffer, 0, n)
                    written += n
                    if (contentLength > 0) {
                        val pct = ((written * 100) / contentLength).toInt().coerceIn(0, 100)
                        if (pct >= lastReported + 10 && pct < 100) {
                            context.reportProgress(TaskProgress.Bounded(step = STEP_DOWNLOAD, percent = pct))
                            lastReported = pct
                        }
                    }
                }
            }
        } catch (e: DownloadTooLargeException) {
            throw e
        } catch (e: Exception) {
            Files.deleteIfExists(target)
            throw e
        }
        return written
    }

    private fun buildRequest(uri: URI, headers: Map<String, String>, readTimeout: Duration): HttpRequest {
        val builder = HttpRequest.newBuilder(uri)
            .timeout(readTimeout)
            .GET()
        for ((key, value) in headers) {
            if (!isRestrictedHeader(key)) {
                builder.header(key, value)
            }
        }
        return builder.build()
    }

    private fun isRestrictedHeader(name: String): Boolean = name.lowercase(Locale.ROOT) in RESTRICTED_HEADERS

    private fun readBodyForFailure(stream: InputStream): String {
        val out = ByteArray(FAILURE_BODY_MAX)
        var read = 0
        while (read < out.size) {
            val n = stream.read(out, read, out.size - read)
            if (n == -1) break
            read += n
        }
        stream.drain()
        return String(out, 0, read, StandardCharsets.UTF_8)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }

    private fun validateSchemeAndHost(uri: URI): TaskImplementationResult.Failure? {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return TaskImplementationResult.Failure(
                "URL scheme must be http or https; got '${uri.scheme ?: ""}'",
            )
        }
        if (uri.host.isNullOrBlank()) {
            return TaskImplementationResult.Failure("URL has no host")
        }
        return null
    }

    private fun rejectPrivateHost(uri: URI, allowPrivateIPs: Boolean): TaskImplementationResult.Failure? {
        if (allowPrivateIPs) return null
        val host = uri.host ?: return null
        val privateAddress = HostValidator.firstPrivateAddress(uri) ?: return null
        return TaskImplementationResult.Failure(
            "Host '$host' resolves to a private/internal address (${privateAddress.hostAddress}); blocked by default",
        )
    }

    private fun URI.maskUserInfo(): String {
        if (userInfo == null) return toString()
        return URI(scheme, null, host, port, path, query, fragment).toString()
    }

    private fun InputStream.drain() {
        val buf = ByteArray(8 * 1024)
        try {
            while (read(buf) != -1) { /* discard */ }
        } catch (_: IOException) {
            // best-effort
        }
    }

    companion object {
        private val RESTRICTED_HEADERS = setOf(
            "connection",
            "content-length",
            "date",
            "expect",
            "from",
            "host",
            "upgrade",
            "via",
            "warning",
        )
    }
}
