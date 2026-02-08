package me.noukakis.re_do.scheduler.port

import java.time.Instant

interface NowPort {
    fun now(): Instant
}