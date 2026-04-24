package me.noukakis.re_do.scheduler.port

import java.time.Duration

class LockTimeoutException(tegId: String, timeout: Duration) :
    RuntimeException("Could not acquire lock for tegId=$tegId after $timeout")

interface MutualExclusionLockPort {
    fun lock(tegId: String)
    fun release(tegId: String)
}