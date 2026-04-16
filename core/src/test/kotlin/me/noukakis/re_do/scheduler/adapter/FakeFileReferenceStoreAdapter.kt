package me.noukakis.re_do.scheduler.adapter

import me.noukakis.re_do.scheduler.model.FileReference
import me.noukakis.re_do.scheduler.port.FileReferenceStorePort

class FakeFileReferenceStoreAdapter : FileReferenceStorePort {
    val savedReferences: MutableList<FileReference> = mutableListOf()

    override fun save(reference: FileReference) {
        savedReferences.add(reference)
    }
}
