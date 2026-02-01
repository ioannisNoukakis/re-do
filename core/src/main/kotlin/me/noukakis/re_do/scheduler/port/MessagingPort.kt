package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.TEGMessageOut

interface MessagingPort {
    fun send(message: TEGMessageOut)
}