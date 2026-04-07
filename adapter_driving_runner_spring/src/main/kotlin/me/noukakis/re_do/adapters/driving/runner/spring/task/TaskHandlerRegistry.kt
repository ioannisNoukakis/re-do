package me.noukakis.re_do.adapters.driving.runner.spring.task

import me.noukakis.re_do.runner.port.TaskHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import kotlin.io.path.absolute

class TaskHandlerRegistry(
    val handlers: List<TaskHandler>
) {
    fun toMap(): Map<String, TaskHandler> {
        return handlers.associateBy { it.implementationName() }
    }

    companion object {
        fun new(pluginDir: Path): TaskHandlerRegistry {
            val logger: Logger = LoggerFactory.getLogger(TaskHandlerRegistry::class.java)!!
            val handlers = loadHandlers(pluginDir)
            logger.info("Loaded ${handlers.size} task handlers from ${pluginDir.absolute()}")
            return TaskHandlerRegistry(handlers)
        }

        private fun loadHandlers(pluginDir: Path): List<TaskHandler> {
            if (!pluginDir.toFile().exists()) return emptyList()

            val jarUrls = pluginDir.toFile()
                .listFiles { f -> f.extension == "jar" }
                ?.map { it.toURI().toURL() }
                ?.toTypedArray()
                ?: return emptyList()

            val classLoader = URLClassLoader(jarUrls, this::class.java.classLoader)

            return ServiceLoader
                .load(TaskHandler::class.java, classLoader)
                .toList()
        }
    }
}