package me.noukakis.re_do.runner.port

import java.nio.file.Path

interface AutoClosablePath : AutoCloseable {
    fun path(): Path
}

interface TempWorkingDirPort {
    fun create(): AutoClosablePath
}