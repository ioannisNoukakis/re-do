package me.noukakis.re_do.adapters.driven.common

import me.noukakis.re_do.common.port.UUIDPort
import java.util.UUID

class StdLibUuidAdapter : UUIDPort {
    override fun next(): String = UUID.randomUUID().toString()
}