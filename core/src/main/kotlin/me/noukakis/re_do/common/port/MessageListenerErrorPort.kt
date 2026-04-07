package me.noukakis.re_do.common.port

interface MessageListenerErrorPort {
    fun onMissingTegId()
    fun onUnreadableMessage(rawBody: ByteArray)
}

