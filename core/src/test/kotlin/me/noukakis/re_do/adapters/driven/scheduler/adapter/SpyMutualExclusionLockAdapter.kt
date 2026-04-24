package me.noukakis.re_do.adapters.driven.scheduler.adapter

import me.noukakis.re_do.scheduler.port.MutualExclusionLockPort

class SpyMutualExclusionLockAdapter : MutualExclusionLockPort {
    val acquiredLocks: MutableList<String> = mutableListOf()
    val releasedLocks: MutableList<String> = mutableListOf()

    override fun lock(tegId: String) {
        acquiredLocks.add(tegId)
    }

    override fun release(tegId: String) {
        releasedLocks.add(tegId)
    }
}