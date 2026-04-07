package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.port.NowPort
import java.time.Instant

class StdLibNowAdapter : NowPort {
    override fun now(): Instant = Instant.now()
}