package me.noukakis.re_do.adapters.common.spring.mongodb

import com.mongodb.client.MongoClient
import io.mongock.driver.mongodb.sync.v4.driver.MongoSync4Driver
import io.mongock.runner.springboot.MongockSpringboot
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.function.Supplier

const val MONGODB_DB_NAME = "test"

fun runMigrations(mongoClient: MongoClient, mongoTemplate: MongoTemplate) {
    GenericApplicationContext().apply {
        registerBean(MongoTemplate::class.java, Supplier { mongoTemplate })
        refresh()
    }.use { springContext ->
        MongockSpringboot.builder()
            .setDriver(MongoSync4Driver.withDefaultLock(mongoClient, MONGODB_DB_NAME))
            .addMigrationScanPackage("me.noukakis.re_do.adapters.common.spring.mongodb.migrations")
            .setSpringContext(springContext)
            .setTransactional(false)
            .buildRunner()
            .execute()
    }
}
