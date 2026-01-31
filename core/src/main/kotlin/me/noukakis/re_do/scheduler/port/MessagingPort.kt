package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.TEGMessage

interface MessagingPort {
    fun send(message: TEGMessage)
}