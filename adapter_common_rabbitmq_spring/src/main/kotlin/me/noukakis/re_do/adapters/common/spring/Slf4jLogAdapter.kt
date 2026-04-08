package me.noukakis.re_do.adapters.common.spring

import me.noukakis.re_do.scheduler.port.LogPort
import me.noukakis.re_do.scheduler.service.TEGScheduler
import org.slf4j.LoggerFactory

class Slf4jLogAdapter : LogPort {
    private val logger = LoggerFactory.getLogger(TEGScheduler::class.java)

    override fun info(tegId: String, message: String) = logger.info("[{}] {}", tegId, message)
    override fun warn(tegId: String, message: String) = logger.warn("[{}] {}", tegId, message)
    override fun debug(tegId: String, message: String) = logger.debug("[{}] {}", tegId, message)
    override fun error(tegId: String, message: String) = logger.error("[{}] {}", tegId, message)
}

