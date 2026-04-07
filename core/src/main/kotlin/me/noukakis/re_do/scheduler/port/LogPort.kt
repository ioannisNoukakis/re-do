package me.noukakis.re_do.scheduler.port

interface LogPort {
    fun info(tegId: String, message: String)
    fun warn(tegId: String, message: String)
    fun debug(tegId: String, message: String)
    fun error(tegId: String, message: String)

    companion object {
        val NoOp: LogPort = object : LogPort {
            override fun info(tegId: String, message: String) = Unit
            override fun warn(tegId: String, message: String) = Unit
            override fun debug(tegId: String, message: String) = Unit
            override fun error(tegId: String, message: String) = Unit
        }
    }
}

