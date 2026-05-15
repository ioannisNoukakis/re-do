package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.port.LockTimeoutException
import me.noukakis.re_do.scheduler.port.MutualExclusionLockPort
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class InMemoryMutualExclusionLockAdapter(
    private val lockTimeout: Duration = Duration.ofSeconds(30),
) : MutualExclusionLockPort {

    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    override fun lock(tegId: String) {
        val lock = locks.computeIfAbsent(tegId) { ReentrantLock() }
        val acquired = lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            throw LockTimeoutException(tegId, lockTimeout)
        }
    }

    override fun release(tegId: String) {
        val lock = locks[tegId] ?: return
        if (lock.isHeldByCurrentThread) {
            lock.unlock()
        }
    }
}
