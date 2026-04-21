package me.noukakis.re_do.adapters.driven.runner

import me.noukakis.re_do.runner.port.AutoClosablePath
import me.noukakis.re_do.runner.port.TempWorkingDirPort
import java.nio.file.Path

class FakeTempWorkingDirAdapter(private val dirPath: Path) : TempWorkingDirPort {
    var wasClosed = false
    override fun create(): AutoClosablePath {
        return object : AutoClosablePath {
            override fun path(): Path {
                return dirPath
            }

            override fun close() {
                wasClosed = true
            }
        }
    }
}