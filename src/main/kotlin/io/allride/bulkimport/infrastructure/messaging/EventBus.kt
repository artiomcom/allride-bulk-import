package io.allride.bulkimport.infrastructure.messaging

import io.allride.bulkimport.domain.event.FileUploadedEvent
import io.allride.bulkimport.domain.event.UsersBatchEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import org.slf4j.LoggerFactory

/**
 * Simple EventBus implementation using Kotlin Coroutines Channels.
 * Supports multiple event types through separate channels.
 * This is a low-level pub/sub mechanism that demonstrates understanding of coroutines.
 */
class EventBus {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val fileUploadChannel = Channel<FileUploadedEvent>(Channel.UNLIMITED)
    private val usersBatchChannel = Channel<UsersBatchEvent>(Channel.UNLIMITED)

    /**
     * Publish a file upload event to the bus.
     * This is a suspend function because Channel.send is suspend.
     */
    suspend fun publish(event: FileUploadedEvent) {
        logger.debug("Publishing FileUploadedEvent: $event")
        fileUploadChannel.send(event)
    }

    /**
     * Publish a users batch event to the bus.
     * This allows other workers/consumers to process batches asynchronously.
     */
    suspend fun publishBatch(event: UsersBatchEvent) {
        logger.debug("Publishing UsersBatchEvent: batch ${event.batchNumber} with ${event.users.size} users from ${event.sourceFileName}")
        usersBatchChannel.send(event)
    }

    /**
     * Subscribe to file upload events.
     * Returns a Flow that can be collected to receive events.
     */
    fun subscribe(): Flow<FileUploadedEvent> {
        logger.debug("New subscriber to file upload events")
        return fileUploadChannel.consumeAsFlow()
    }

    /**
     * Subscribe to users batch events.
     * Returns a Flow that can be collected to receive batch events.
     * Useful for downstream processing (e.g., saving to database, sending to external services).
     */
    fun subscribeBatches(): Flow<UsersBatchEvent> {
        logger.debug("New subscriber to users batch events")
        return usersBatchChannel.consumeAsFlow()
    }
}
