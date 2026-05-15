package me.noukakis.re_do.adapters.common.spring.mongodb

import com.mongodb.client.model.changestream.ChangeStreamDocument
import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbTEGEvent
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.TegEventListener
import me.noukakis.re_do.scheduler.port.TegEventStreamPort
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.getCollectionName
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest
import org.springframework.data.mongodb.core.messaging.MessageListener
import org.springframework.data.mongodb.core.messaging.MessageListenerContainer
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class MongodbTegEventStreamAdapter(
    private val mongoTemplate: MongoTemplate,
    private val messageListenerContainer: MessageListenerContainer,
) : TegEventStreamPort {

    private val logger = LoggerFactory.getLogger(MongodbTegEventStreamAdapter::class.java)
    private val collectionName = mongoTemplate.getCollectionName<MongodbTEGEvent>()

    override fun stream(tegId: String, listener: TegEventListener): AutoCloseable {
        val resumeAt = Instant.now()
        val seenIds = ConcurrentHashMap.newKeySet<String>()

        val history = mongoTemplate.find(
            Query.query(Criteria.where(MongodbTEGEvent::tegId.name).`is`(tegId)),
            MongodbTEGEvent::class.java,
        )
        for (mongoEvent in history) {
            seenIds.add(mongoEvent.id)
            val event = mongoEvent.toModel()
            listener.onEvent(event)
            if (event.isTerminal()) {
                listener.onComplete()
                return AutoCloseable {}
            }
        }

        val changeStreamListener = MessageListener<ChangeStreamDocument<Document>, MongodbTEGEvent> { message ->
            try {
                val mongoEvent = message.body ?: return@MessageListener
                if (!seenIds.add(mongoEvent.id)) return@MessageListener
                val event = mongoEvent.toModel()
                listener.onEvent(event)
                if (event.isTerminal()) {
                    listener.onComplete()
                }
            } catch (t: Throwable) {
                logger.error("Error delivering change-stream event for tegId={}", tegId, t)
                listener.onError(t)
            }
        }

        val request = ChangeStreamRequest.builder(changeStreamListener)
            .collection(collectionName)
            .filter(
                Aggregation.newAggregation(
                    Aggregation.match(
                        Criteria.where("fullDocument.${MongodbTEGEvent::tegId.name}").`is`(tegId),
                    ),
                ),
            )
            .resumeAt(resumeAt)
            .build()

        val subscription = messageListenerContainer.register(request, MongodbTEGEvent::class.java)
        return AutoCloseable { messageListenerContainer.remove(subscription) }
    }

    private fun TEGEvent.isTerminal(): Boolean = this is TEGEvent.NoMoreTasksToSchedule || this is TEGEvent.TEGFailed
}
