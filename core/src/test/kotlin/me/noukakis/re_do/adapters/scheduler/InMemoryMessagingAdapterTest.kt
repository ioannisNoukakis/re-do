package me.noukakis.re_do.adapters.scheduler

import me.noukakis.re_do.adapters.common.InMemoryMessagingAdapter
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InMemoryMessagingAdapterTest {
    private lateinit var adapter: InMemoryMessagingAdapter

    @BeforeEach
    fun setup() {
        adapter = InMemoryMessagingAdapter()
    }

    @Nested
    inner class SendingOutgoingMessages {
        @Test
        fun `sent messages are queued`() {
            val message = TEGMessageOut.TEGRunTaskMessage(
                taskName = "task1",
                implementationName = "task1Impl",
                artefacts = emptyList(),
                arguments = emptyList(),
            )

            adapter.send(message)

            assertEquals(listOf(message), adapter.outgoingMessages)
        }

        @Test
        fun `multiple sent messages are queued in order`() {
            val message1 = TEGMessageOut.TEGRunTaskMessage(
                taskName = "task1",
                implementationName = "task1Impl",
                artefacts = emptyList(),
                arguments = emptyList(),
            )
            val message2 = TEGMessageOut.TEGRunTaskMessage(
                taskName = "task2",
                implementationName = "task1Impl",
                artefacts = listOf(
                    TEGArtefact.TEGArtefactStringValue("input", "value")
                ),
                arguments = listOf("arg1", "arg2"),
            )

            adapter.send(message1)
            adapter.send(message2)

            assertEquals(listOf(message1, message2), adapter.outgoingMessages)
        }

        @Test
        fun `registered callback is invoked when message is sent`() {
            val receivedMessages = mutableListOf<TEGMessageOut>()
            adapter.onOutgoingMessage { receivedMessages.add(it) }

            val message = TEGMessageOut.TEGRunTaskMessage(
                taskName = "task1",
                implementationName = "task1Impl",
                artefacts = emptyList(),
                arguments = emptyList(),
            )
            adapter.send(message)

            assertEquals(listOf(message), receivedMessages)
        }

        @Test
        fun `multiple callbacks are invoked when message is sent`() {
            val receivedMessages1 = mutableListOf<TEGMessageOut>()
            val receivedMessages2 = mutableListOf<TEGMessageOut>()
            adapter.onOutgoingMessage { receivedMessages1.add(it) }
            adapter.onOutgoingMessage { receivedMessages2.add(it) }

            val message = TEGMessageOut.TEGRunTaskMessage(
                taskName = "task1",
                implementationName = "task1Impl",
                artefacts = emptyList(),
                arguments = emptyList(),
            )
            adapter.send(message)

            assertEquals(listOf(message), receivedMessages1)
            assertEquals(listOf(message), receivedMessages2)
        }
    }

    @Nested
    inner class ReceivingIncomingMessages {
        @Test
        fun `received messages are queued`() {
            val message = TEGMessageIn.TEGTaskResultMessage(
                taskName = "task1",
                outputArtefacts = emptyList()
            )

            adapter.receive(message)

            assertEquals(listOf(message), adapter.incomingMessages)
        }

        @Test
        fun `multiple received messages are queued in order`() {
            val message1 = TEGMessageIn.TEGTaskResultMessage(
                taskName = "task1",
                outputArtefacts = emptyList()
            )
            val message2 = TEGMessageIn.TEGTaskFailedMessage(
                taskName = "task2",
                reason = "Something went wrong"
            )
            val message3 = TEGMessageIn.TEGTaskProgressMessage(
                taskName = "task1",
                progress = 50
            )
            val message4 = TEGMessageIn.TEGTaskLogMessage(
                taskName = "task1",
                log = "Processing..."
            )

            adapter.receive(message1)
            adapter.receive(message2)
            adapter.receive(message3)
            adapter.receive(message4)

            assertEquals(listOf(message1, message2, message3, message4), adapter.incomingMessages)
        }

        @Test
        fun `registered callback is invoked when message is received`() {
            val receivedMessages = mutableListOf<TEGMessageIn>()
            adapter.onIncomingMessage { receivedMessages.add(it) }

            val message = TEGMessageIn.TEGTaskResultMessage(
                taskName = "task1",
                outputArtefacts = emptyList()
            )
            adapter.receive(message)

            assertEquals(listOf(message), receivedMessages)
        }

        @Test
        fun `multiple callbacks are invoked when message is received`() {
            val receivedMessages1 = mutableListOf<TEGMessageIn>()
            val receivedMessages2 = mutableListOf<TEGMessageIn>()
            adapter.onIncomingMessage { receivedMessages1.add(it) }
            adapter.onIncomingMessage { receivedMessages2.add(it) }

            val message = TEGMessageIn.TEGTaskResultMessage(
                taskName = "task1",
                outputArtefacts = emptyList()
            )
            adapter.receive(message)

            assertEquals(listOf(message), receivedMessages1)
            assertEquals(listOf(message), receivedMessages2)
        }
    }

    @Nested
    inner class ClearingQueues {
        @Test
        fun `clearing outgoing messages empties the queue`() {
            adapter.send(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = "task1",
                    implementationName = "task1Impl",
                    artefacts = emptyList(),
                    arguments = emptyList()
                )
            )
            adapter.send(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = "task2",
                    implementationName = "task2Impl",
                    artefacts = emptyList(),
                    arguments = emptyList()
                )
            )

            adapter.clearOutgoingMessages()

            assertTrue(adapter.outgoingMessages.isEmpty())
        }

        @Test
        fun `clearing incoming messages empties the queue`() {
            adapter.receive(TEGMessageIn.TEGTaskResultMessage("task1", emptyList()))
            adapter.receive(TEGMessageIn.TEGTaskFailedMessage("task2", "error"))

            adapter.clearIncomingMessages()

            assertTrue(adapter.incomingMessages.isEmpty())
        }

        @Test
        fun `clearing all messages empties both queues`() {
            adapter.send(TEGMessageOut.TEGRunTaskMessage(
                taskName = "task1",
                implementationName = "task1Impl",
                artefacts = emptyList(),
                arguments = emptyList()
            ))
            adapter.receive(TEGMessageIn.TEGTaskResultMessage("task1", emptyList()))

            adapter.clearAll()

            assertTrue(adapter.outgoingMessages.isEmpty())
            assertTrue(adapter.incomingMessages.isEmpty())
        }
    }
}


