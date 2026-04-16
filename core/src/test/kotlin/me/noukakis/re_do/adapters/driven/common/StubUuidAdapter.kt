package me.noukakis.re_do.adapters.driven.common

import me.noukakis.re_do.common.port.UUIDPort

class StubUuidAdapter(
    var uuidsToReturn: List<String> = emptyList(),
) : UUIDPort {
    private var index = 0
    override fun next() = uuidsToReturn[index++]
}