package me.noukakis.re_do.scheduler.adapter

import arrow.core.Tuple4
import me.noukakis.re_do.scheduler.port.FileStoragePort
import me.noukakis.re_do.scheduler.port.StoredFileRef
import java.io.InputStream

class StubFileStorageAdapter(
    val toReturn: MutableMap<Tuple4<String, String, String, Long>, StoredFileRef>,
) : FileStoragePort {
    override fun upload(fileId: String, filename: String, contentType: String, contentLength: Long, stream: InputStream): StoredFileRef =
        toReturn[Tuple4(fileId, filename, contentType, contentLength)]
            ?: throw IllegalStateException("No stubbed response for fileId=$fileId, filename=$filename, contentType=$contentType, contentLength=$contentLength")
}
