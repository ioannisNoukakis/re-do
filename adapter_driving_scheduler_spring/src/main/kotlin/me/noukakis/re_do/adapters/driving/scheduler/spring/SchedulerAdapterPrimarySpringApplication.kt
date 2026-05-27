package me.noukakis.re_do.adapters.driving.scheduler.spring

import me.noukakis.re_do.adapters.driving.scheduler.spring.configuration.AuthProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "me.noukakis.re_do.adapters.driving.scheduler",
        "me.noukakis.re_do.adapters.common.spring",
    ],
)
@EnableConfigurationProperties(AuthProperties::class)
class SchedulerAdapterPrimarySpringApplication

fun main(args: Array<String>) {
    runApplication<SchedulerAdapterPrimarySpringApplication>(*args)
}
