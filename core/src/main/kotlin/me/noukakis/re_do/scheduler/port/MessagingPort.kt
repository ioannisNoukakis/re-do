package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.common.model.TEGMessageOut

interface MessagingPort {
    fun send(message: TEGMessageOut)
}