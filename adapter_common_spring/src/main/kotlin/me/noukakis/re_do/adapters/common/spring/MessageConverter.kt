package me.noukakis.re_do.adapters.common.spring

import me.noukakis.re_do.adapters.common.spring.configuration.TEGArtefactMixin
import me.noukakis.re_do.adapters.common.spring.configuration.TEGMessageInMixin
import me.noukakis.re_do.adapters.common.spring.configuration.TEGMessageOutMixin
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.scheduler.model.TEGArtefact
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

object MessageConverter {
    fun new(): JacksonJsonMessageConverter {
        val mapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .addMixIn(TEGArtefact::class.java, TEGArtefactMixin::class.java)
            .addMixIn(TEGMessageIn::class.java, TEGMessageInMixin::class.java)
            .addMixIn(TEGMessageOut::class.java, TEGMessageOutMixin::class.java)
            .addMixIn(TEGMessageOut.TEGRunTaskMessage::class.java, TEGMessageOutMixin.TEGRunTaskMessageMixin::class.java)
            .build()
        return JacksonJsonMessageConverter(mapper)
    }
}