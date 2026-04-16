package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.FileReference

interface FileReferenceStorePort {
    fun save(reference: FileReference)
}
