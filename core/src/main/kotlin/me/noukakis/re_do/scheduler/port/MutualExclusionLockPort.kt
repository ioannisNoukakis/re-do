package me.noukakis.re_do.scheduler.port

interface MutualExclusionLockPort {
    fun lock(tegId: String)
    fun release(tegId: String)

}