package me.noukakis.re_do.adapters.common.spring.mongodb

import me.noukakis.re_do.adapters.common.spring.mongodb.model.MongodbTEGEvent
import me.noukakis.re_do.adapters.common.spring.mongodb.model.toMongoModel
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregateStream
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOptions
import org.springframework.data.mongodb.core.getCollectionName
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.stream.Stream
import kotlin.reflect.KClass


class MongodbPersistenceAdapter(
    private val mongodbTemplate: MongoTemplate,
    private val cursorBatchSizeForGetAllTegNotEvents: Int,
    private val tegEventLookbackDuration: Duration,
    private val getNow: () -> Instant = { Instant.now() }
) : PersistencePort {
    override fun saveEvents(
        tegId: String,
        events: List<TEGEvent>
    ) {
        if (events.isEmpty()) {
            return
        }

        mongodbTemplate.insertAll(events
            .map { it.toMongoModel(tegId, UUID.randomUUID().toString()) })
    }

    override fun getEventsForTeg(
        tegId: String,
        filter: TegEventFilter
    ): List<TEGEvent> {
        val query = Query.query(Criteria.where(MongodbTEGEvent::tegId.name).`is`(tegId))

        if (filter == TegEventFilter.StateEvent) {
            query.addCriteria(Criteria.where(MongodbTEGEvent::type.name).nin(listOf(
                TEGEvent.Log::class.simpleName,
                TEGEvent.Progress::class.simpleName
            )))
        }

        return mongodbTemplate.find(query, MongodbTEGEvent::class.java)
            .map(MongodbTEGEvent::toModel)
    }

    override fun getTegsThatDontHaveEvents(klass: List<KClass<out TEGEvent>>): Stream<Pair<String, List<TEGEvent>>> {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(
                Criteria.where(MongodbTEGEvent::timestamp.name).gte(getNow().minus(tegEventLookbackDuration))
            ),
            Aggregation.group(MongodbTEGEvent::tegId.name)
                .push($$$"$$ROOT").`as`("events")
                .addToSet("type").`as`("types"),
            Aggregation.match(
                Criteria.where("types").not().`in`(klass.map { it.simpleName })
            )
        ).withOptions(
            AggregationOptions.builder()
                .cursorBatchSize(cursorBatchSizeForGetAllTegNotEvents)
                .build()
        )
        return mongodbTemplate.aggregateStream<GroupedEvents>(
            aggregation,
            mongodbTemplate.getCollectionName<MongodbTEGEvent>()
        )            .map { ge -> ge._id to ge.events.map { it.toModel() } }
    }

    data class GroupedEvents(
        val _id: String,
        val events: List<MongodbTEGEvent>,
        val types: Set<String>,
    )
}