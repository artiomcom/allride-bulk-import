package io.allride.bulkimport.infrastructure.messaging

import io.allride.bulkimport.domain.event.FileUploadedEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventBusTest {

    private lateinit var eventBus: EventBus

    @BeforeEach
    fun setUp() {
        eventBus = EventBus()
    }

    @Test
    fun `should publish and subscribe to events`() = runTest {
        val event = FileUploadedEvent(
            filePath = "/path/to/file.csv",
            fileName = "file.csv"
        )

        eventBus.publish(event)
        val subscription = eventBus.subscribe()
        val receivedEvent = subscription.first()

        assertNotNull(receivedEvent)
        assertEquals(event.filePath, receivedEvent.filePath)
        assertEquals(event.fileName, receivedEvent.fileName)
    }

    @Test
    fun `should handle multiple events`() = runTest {
        val event1 = FileUploadedEvent("/path/to/file1.csv", "file1.csv")
        val event2 = FileUploadedEvent("/path/to/file2.csv", "file2.csv")

        val subscription = eventBus.subscribe()
        
        eventBus.publish(event1)
        eventBus.publish(event2)

        val receivedEvents = subscription.take(2).toList()
        
        assertEquals(2, receivedEvents.size)
        assertEquals(event1.fileName, receivedEvents[0].fileName)
        assertEquals(event2.fileName, receivedEvents[1].fileName)
    }

    @Test
    fun `should publish events with correct data`() = runTest {
        val filePath = "/uploads/test-file.csv"
        val fileName = "test-file.csv"
        val event = FileUploadedEvent(filePath, fileName)

        eventBus.publish(event)
        val subscription = eventBus.subscribe()
        val receivedEvent = subscription.first()

        assertEquals(filePath, receivedEvent.filePath)
        assertEquals(fileName, receivedEvent.fileName)
    }
}

