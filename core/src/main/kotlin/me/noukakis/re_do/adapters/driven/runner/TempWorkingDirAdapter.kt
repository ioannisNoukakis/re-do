package me.noukakis.re_do.adapters.driven.runner

import me.noukakis.re_do.runner.port.AutoClosablePath
import me.noukakis.re_do.runner.port.TempWorkingDirPort
import java.nio.file.Files
import java.nio.file.Path

class TempWorkingDirAdapter : TempWorkingDirPort {
    override fun create(): AutoClosablePath {
        val dir = Files.createTempDirectory("re-do-")
        return object : AutoClosablePath {
            override fun path(): Path = dir
            override fun close() {
                dir.toFile().deleteRecursively()
            }
        }
    }
}
