package me.noukakis.re_do.scheduler.adapter

import me.noukakis.re_do.scheduler.model.TEGMessage
import me.noukakis.re_do.scheduler.port.MessagingPort

class SpyMessagingAdapter : MessagingPort {
    val sentMessages = mutableListOf<TEGMessage>()

    override fun send(message: TEGMessage) {
        sentMessages.add(message)
    }
}