package me.noukakis.re_do.adapters.driven.runner

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class TempWorkingDirAdapterIT {

    private val sut = TempWorkingDirAdapter()

    @Test
    fun `create returns an existing directory`() {
        sut.create().use { workDir ->
            assertTrue(Files.exists(workDir.path()))
            assertTrue(Files.isDirectory(workDir.path()))
        }
    }

    @Test
    fun `create returns a distinct directory on each call`() {
        val first = sut.create()
        val second = sut.create()

        assertNotEquals(first.path(), second.path())

        first.close()
        second.close()
    }

    @Test
    fun `close deletes the directory`() {
        val workDir = sut.create()
        val path = workDir.path()

        workDir.close()

        assertFalse(Files.exists(path))
    }

    @Test
    fun `close deletes the directory and its contents recursively`() {
        val workDir = sut.create()
        Files.writeString(workDir.path().resolve("data.txt"), "some content")
        val path = workDir.path()

        workDir.close()

        assertFalse(Files.exists(path))
        assertFalse(Files.exists(workDir.path().resolve("data.txt")))
    }
}
