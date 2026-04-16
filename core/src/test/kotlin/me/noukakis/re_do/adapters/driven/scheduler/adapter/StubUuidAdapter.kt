package me.noukakis.re_do.adapters.driven.scheduler.adapter

import me.noukakis.re_do.scheduler.port.UUIDPort

class StubUuidAdapter(
    var tegUuid: String
) : UUIDPort {
    override fun generateUUID() = tegUuid
}