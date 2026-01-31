package me.noukakis.re_do.scheduler.adapter

import me.noukakis.re_do.scheduler.port.UUIDPort

class StubUuidAdapter(
    var tegUuid: String
) : UUIDPort {
    override fun generateUUID() = tegUuid
}