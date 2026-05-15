package me.noukakis.re_do.scheduler.service

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.StreamTegEventsError
import me.noukakis.re_do.scheduler.model.TEGEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

private const val TEG_ID = "test-teg-id"
private const val OWNER_SUB = "user-owner"
private const val INTRUDER_SUB = "user-intruder"
private val OWNER_IDENTITY = Identity(sub = OWNER_SUB, roles = listOf("scheduler-user"))
private val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val T1: Instant = Instant.parse("2026-01-01T00:00:01Z")
private val T2: Instant = Instant.parse("2026-01-01T00:00:02Z")

class StreamTegEventsUseCaseTest {
    private lateinit var sut: StreamTegEventsUseCaseSutBuilder

    @BeforeEach
    fun setup() {
        sut = StreamTegEventsUseCaseSutBuilder()
    }

    @Test
    fun `should return TegNotFound when the teg has no SubmitterIdentity event`() {
        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = OWNER_SUB)

        sut.thenTheResultIsAnError(StreamTegEventsError.TegNotFound)
    }

    @Test
    fun `should return Forbidden when the caller sub does not match the submitter`() {
        sut.givenTheExistingEvents(
            mapOf(TEG_ID to listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))),
        )

        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = INTRUDER_SUB)

        sut.thenTheResultIsAnError(StreamTegEventsError.Forbidden)
    }

    @Test
    fun `should return a subscription handle when the caller is the submitter`() {
        sut.givenTheExistingEvents(
            mapOf(TEG_ID to listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))),
        )

        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = OWNER_SUB)

        sut.thenAHandleWasReturned()
    }

    @Test
    fun `should replay all existing events to the listener when the caller is the submitter`() {
        sut.givenTheExistingEvents(
            mapOf(
                TEG_ID to listOf(
                    TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0),
                    TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
                ),
            ),
        )

        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = OWNER_SUB)

        sut.thenTheListenerReceivedEvents(
            TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0),
            TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
        )
    }

    @Test
    fun `should deliver newly-appended events to the listener after subscription`() {
        sut.givenTheExistingEvents(
            mapOf(TEG_ID to listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))),
        )
        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = OWNER_SUB)

        sut.thenTheListenerReceivedEvents(
            TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0),
        )

        sut.whenAppendingEvents(
            tegId = TEG_ID,
            events = listOf(TEGEvent.Scheduled(taskName = "task-a", timestamp = T1)),
        )

        sut.thenTheListenerReceivedEvents(
            TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0),
            TEGEvent.Scheduled(taskName = "task-a", timestamp = T1),
        )
    }

    @Test
    fun `should complete the listener when a terminal event is appended`() {
        sut.givenTheExistingEvents(
            mapOf(TEG_ID to listOf(TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0))),
        )
        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = OWNER_SUB)

        sut.whenAppendingEvents(
            tegId = TEG_ID,
            events = listOf(TEGEvent.NoMoreTasksToSchedule(timestamp = T2)),
        )

        sut.thenTheListenerIsCompleted()
    }

    @Test
    fun `should complete the listener when a terminal event is already in history`() {
        sut.givenTheExistingEvents(
            mapOf(
                TEG_ID to listOf(
                    TEGEvent.SubmitterIdentity(OWNER_IDENTITY, T0),
                    TEGEvent.NoMoreTasksToSchedule(timestamp = T2),
                ),
            ),
        )

        sut.whenStreamingEvents(tegId = TEG_ID, callerSub = OWNER_SUB)

        sut.thenTheListenerIsCompleted()
    }
}
