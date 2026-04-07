package me.noukakis.re_do.adapters.driving.runner.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = [
	"me.noukakis.re_do.adapters.driving.runner.spring",
	"me.noukakis.re_do.adapters.common.spring",
])
class RunnerApplication

fun main(args: Array<String>) {
	runApplication<RunnerApplication>(*args)
}
