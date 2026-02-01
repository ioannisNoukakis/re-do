package me.noukakis.re_do.scheduler.adapter

import me.noukakis.re_do.scheduler.model.TEGMessageOut
import me.noukakis.re_do.scheduler.port.MessagingPort

class SpyMessagingAdapter : MessagingPort {
    val sentMessages = mutableListOf<TEGMessageOut>()

    override fun send(message: TEGMessageOut) {
        sentMessages.add(message)
    }
}