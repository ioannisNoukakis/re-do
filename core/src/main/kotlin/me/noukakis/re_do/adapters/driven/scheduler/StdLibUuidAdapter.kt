package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.port.UUIDPort
import java.util.UUID

class StdLibUuidAdapter : UUIDPort {
    override fun generateUUID(): String = UUID.randomUUID().toString()
}