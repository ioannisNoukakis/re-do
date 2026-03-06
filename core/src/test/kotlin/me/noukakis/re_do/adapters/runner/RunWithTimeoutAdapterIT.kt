package me.noukakis.re_do.adapters.runner

import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.noukakis.re_do.runner.port.TaskTimedOut
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunWithTimeoutAdapterIT {

    private val adapter = RunWithTimeoutAdapter()

    @Test
    fun `zero timeout should timeout immediately`() = runTest {
        val result = adapter.execute(
            supplier = { "result" },
            timeout = 0.milliseconds
        )

        assertEquals(TaskTimedOut.left(), result)
    }

    @Test
    fun `task that completes within the timeout should return its result`() = runTest {
        val result = adapter.execute(
            supplier = { "done" },
            timeout = 1.seconds
        )

        assertEquals("done".right(), result)
    }

    @Test
    fun `task that takes longer than the timeout should return TaskTimedOut`() = runTest {
        val result = adapter.execute(
            supplier = { delay(500.milliseconds); "too late" },
            timeout = 10.milliseconds
        )

        assertEquals(TaskTimedOut.left(), result)
    }
}