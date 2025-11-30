package io.allride.bulkimport

import io.allride.bulkimport.application.service.CsvProcessingService
import io.allride.bulkimport.application.service.ProcessingErrorService
import io.allride.bulkimport.application.service.UserStorageService
import io.allride.bulkimport.application.worker.CsvProcessingWorker
import io.allride.bulkimport.domain.event.UsersBatchEvent
import io.allride.bulkimport.infrastructure.messaging.EventBus
import io.allride.bulkimport.infrastructure.storage.FileStorageService
import io.allride.bulkimport.interfaces.rest.configureRouting
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val eventBus = EventBus()
    val fileStorageService = FileStorageService("uploads")
    val csvProcessingService = CsvProcessingService()
    val processingErrorService = ProcessingErrorService()
    val userStorageService = UserStorageService()

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val worker = CsvProcessingWorker(
        eventBus = eventBus,
        fileStorageService = fileStorageService,
        csvProcessingService = csvProcessingService,
        processingErrorService = processingErrorService
    )

    applicationScope.launch {
        worker.start()
    }

    applicationScope.launch {
        eventBus.subscribeBatches()
            .onEach { batchEvent ->
                userStorageService.saveBatch(batchEvent.users)
            }
            .catch { e ->
                logger.error("Error processing user batches", e)
            }
            .launchIn(applicationScope)
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }

    configureRouting(
        eventBus = eventBus,
        fileStorageService = fileStorageService,
        userStorageService = userStorageService,
        processingErrorService = processingErrorService
    )
}

