package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort

class InMemoryFileReferenceStoreAdapter : FileReferenceStorePort {
    val references: MutableList<FileReference> = mutableListOf()

    override fun save(reference: FileReference) {
        references.add(reference)
    }
}
