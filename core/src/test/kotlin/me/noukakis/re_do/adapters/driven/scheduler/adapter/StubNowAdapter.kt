package me.noukakis.re_do.adapters.driven.scheduler.adapter

import me.noukakis.re_do.scheduler.port.NowPort
import java.time.Instant

class StubNowAdapter : NowPort {
    var toReturn: MutableList<Instant> = mutableListOf()

    override fun now(): Instant {
        if (toReturn.isEmpty()) {
            throw IllegalStateException("No more timestamps to return")
        }
        return toReturn.removeAt(0)
    }
}