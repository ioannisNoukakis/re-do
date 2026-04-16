package me.noukakis.re_do.scheduler.model

import me.noukakis.re_do.common.model.Identity

data class FileReference(
    val fileId: String,
    val ref: String,
    val storedWith: String,
    val uploadedBy: Identity,
)
