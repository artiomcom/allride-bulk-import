package io.allride.bulkimport.interfaces.rest

import io.allride.bulkimport.application.service.ProcessingErrorService
import io.allride.bulkimport.application.service.UserStorageService
import io.allride.bulkimport.domain.event.FileUploadedEvent
import io.allride.bulkimport.infrastructure.messaging.EventBus
import io.allride.bulkimport.infrastructure.storage.FileStorageService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

fun Application.configureRouting(
    eventBus: EventBus,
    fileStorageService: FileStorageService,
    userStorageService: UserStorageService,
    processingErrorService: ProcessingErrorService
) {
    val logger = LoggerFactory.getLogger("Routing")

    routing {
        post("/api/files/upload") {
            try {
                val multipart = call.receiveMultipart()
                var filePart: PartData.FileItem? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        filePart = part
                    } else {
                        part.dispose()
                    }
                }

                val filePartToProcess = filePart ?: run {
                    call.respond(HttpStatusCode.BadRequest, UploadResponse(
                        success = false,
                        message = "No file provided",
                        fileName = null
                    ))
                    return@post
                }

                val fileName = filePartToProcess.originalFileName ?: ""
                if (!fileName.lowercase().endsWith(".csv")) {
                    filePartToProcess.dispose()
                    call.respond(HttpStatusCode.BadRequest, UploadResponse(
                        success = false,
                        message = "File must be a CSV file",
                        fileName = null
                    ))
                    return@post
                }

                val filePath = fileStorageService.storeFile(filePartToProcess)
                filePartToProcess.dispose()

                logger.info("File stored: $fileName at $filePath")

                launch {
                    eventBus.publish(
                        FileUploadedEvent(
                            filePath = filePath,
                            fileName = fileName
                        )
                    )
                }

                call.respond(HttpStatusCode.OK, UploadResponse(
                    success = true,
                    message = "File uploaded successfully, processing started",
                    fileName = fileName
                ))
            } catch (e: Exception) {
                logger.error("Error uploading file", e)
                call.respond(HttpStatusCode.InternalServerError, UploadResponse(
                    success = false,
                    message = "Error uploading file: ${e.message}",
                    fileName = null
                ))
            }
        }

        get("/api/files/users") {
            val users = userStorageService.getAllUsers()
            call.respond(UsersResponse(
                count = users.size,
                users = users
            ))
        }

        get("/api/files/users/count") {
            val count = userStorageService.getUserCount()
            call.respond(mapOf("count" to count))
        }

        get("/api/files/processing-errors") {
            val latestErrors = processingErrorService.getLatestErrors()
            call.respond(ProcessingErrorsResponse(
                hasErrors = latestErrors != null && latestErrors.errors.isNotEmpty(),
                errors = latestErrors?.errors ?: emptyList(),
                processedCount = latestErrors?.processedCount ?: 0,
                totalRows = latestErrors?.totalRows ?: 0,
                fileName = latestErrors?.fileName
            ))
        }

        get("/api/files/processing-errors/{fileName}") {
            val fileName = call.parameters["fileName"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "File name required")
                return@get
            }

            val errors = processingErrorService.getErrors(fileName)
            if (errors != null) {
                call.respond(ProcessingErrorsResponse(
                    hasErrors = errors.errors.isNotEmpty(),
                    errors = errors.errors,
                    processedCount = errors.processedCount,
                    totalRows = errors.totalRows,
                    fileName = errors.fileName
                ))
            } else {
                call.respond(HttpStatusCode.NotFound, "No errors found for file: $fileName")
            }
        }
    }
}

@Serializable
data class UploadResponse(
    val success: Boolean,
    val message: String,
    val fileName: String?
)

@Serializable
data class UsersResponse(
    val count: Int,
    val users: List<io.allride.bulkimport.domain.model.User>
)

@Serializable
data class ProcessingErrorsResponse(
    val hasErrors: Boolean,
    val errors: List<String>,
    val processedCount: Int,
    val totalRows: Int,
    val fileName: String?
)

