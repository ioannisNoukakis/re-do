package me.noukakis.re_do.runner.port

import me.noukakis.re_do.common.model.TEGMessageIn

interface MessagingPort {
    fun send(message: TEGMessageIn)
}