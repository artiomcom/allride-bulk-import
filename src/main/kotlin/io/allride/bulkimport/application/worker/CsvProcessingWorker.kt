package io.allride.bulkimport.application.worker

import io.allride.bulkimport.application.service.CsvProcessingService
import io.allride.bulkimport.application.service.ProcessingErrorService
import io.allride.bulkimport.domain.event.FileUploadedEvent
import io.allride.bulkimport.domain.event.UsersBatchEvent
import io.allride.bulkimport.infrastructure.messaging.EventBus
import io.allride.bulkimport.infrastructure.storage.FileStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory

/**
 * Worker that processes CSV files asynchronously.
 * Uses launch to start processing in a coroutine scope.
 * Subscribes to FileUploadedEvent stream and passes files to CsvProcessingService.
 * This demonstrates low-level understanding of coroutines.
 */
class CsvProcessingWorker(
    private val eventBus: EventBus,
    private val fileStorageService: FileStorageService,
    private val csvProcessingService: CsvProcessingService,
    private val processingErrorService: ProcessingErrorService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Starts listening to events and processing CSV files.
     */
    fun start() {
        logger.info("Starting CSV Processing Worker...")

        eventBus.subscribe()
            .onEach { event ->
                processFile(event)
            }
            .catch { e ->
                logger.error("Error in event stream, continuing...", e)
            }
            .launchIn(scope)

        logger.info("CSV Processing Worker started")
    }

    /**
     * Processes a single uploaded CSV file event.
     */
    private suspend fun processFile(event: FileUploadedEvent) {
        logger.info("Received FileUploadedEvent: ${event.fileName} at ${event.filePath}")

        try {
            val filePath = fileStorageService.getFile(event.filePath)

            var batchNumber = 0
            val result = csvProcessingService.processCsvFile(filePath) { batch ->
                batchNumber++

                eventBus.publishBatch(
                    UsersBatchEvent(
                        users = batch,
                        sourceFileName = event.fileName,
                        batchNumber = batchNumber
                    )
                )

                logger.debug(
                    "Published batch {} of {} users for file {} to event bus",
                    batchNumber,
                    batch.size,
                    event.fileName
                )
            }

            logger.info(
                "Processing completed for {}. Processed: {}, Errors: {}, Total rows: {}",
                event.fileName,
                result.processedCount,
                result.errors.size,
                result.totalRows
            )

            val errorMessages: List<String> = result.errors.map { error ->
                if (error.rowIndex > 0) {
                    "Row ${error.rowIndex}: ${error.message}"
                } else {
                    error.message
                }
            }

            processingErrorService.saveErrors(
                fileName = event.fileName,
                errors = errorMessages,
                processedCount = result.processedCount,
                totalRows = result.totalRows
            )

            if (result.errors.isNotEmpty()) {
                logger.warn("Errors encountered during processing of {}:", event.fileName)
                result.errors.forEach { error ->
                    if (error.rowIndex > 0) {
                        logger.warn("  - [row={}] {}", error.rowIndex, error.message)
                    } else {
                        logger.warn("  - {}", error.message)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process file: ${event.filePath}", e)

            val errorMessage = "Failed to process file: ${e.message ?: "unknown error"}"

            processingErrorService.saveErrors(
                fileName = event.fileName,
                errors = listOf(errorMessage),
                processedCount = 0,
                totalRows = 0
            )
        }
    }
}
