package io.allride.bulkimport.integration

import io.allride.bulkimport.application.service.CsvProcessingService
import io.allride.bulkimport.application.service.ProcessingErrorService
import io.allride.bulkimport.application.service.UserStorageService
import io.allride.bulkimport.application.worker.CsvProcessingWorker
import io.allride.bulkimport.infrastructure.messaging.EventBus
import io.allride.bulkimport.infrastructure.storage.FileStorageService
import io.allride.bulkimport.interfaces.rest.configureRouting
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FileUploadIntegrationTest {

    @BeforeEach
    fun setUp() {
        val testUploadsDir = Path.of("test-uploads")
        if (Files.exists(testUploadsDir)) {
            Files.walk(testUploadsDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `should upload and process CSV file end-to-end`() = testApplication {
        val eventBus = EventBus()
        val fileStorageService = FileStorageService("test-uploads")
        val csvProcessingService = CsvProcessingService()
        val userStorageService = UserStorageService()
        val processingErrorService = ProcessingErrorService()

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
                }
                .launchIn(applicationScope)
        }

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(CORS) {
                anyHost()
            }
            configureRouting(
                eventBus = eventBus,
                fileStorageService = fileStorageService,
                userStorageService = userStorageService,
                processingErrorService = processingErrorService
            )
        }

        runBlocking {
            val initialUserCount = userStorageService.getUserCount()
            val csvContent = """
                id,firstName,lastName,email
                1,Bruce,lee,bruce.lee@mail.com
                2,Anna,lee,anna.lee@mail.com
                3,Bob,lee,bob.lee@mail.com
            """.trimIndent()

            val csvFile = createTempCsvFile("test-users.csv", csvContent)

            val response = client.post("/api/files/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", csvFile.toFile().readBytes(), Headers.build {
                                append(HttpHeaders.ContentType, "text/csv")
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"test-users.csv\"")
                            })
                        }
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)

            var finalUserCount = userStorageService.getUserCount()
            var attempts = 0
            val maxAttempts = 10
            while (finalUserCount < initialUserCount + 3 && attempts < maxAttempts) {
                delay(500)
                finalUserCount = userStorageService.getUserCount()
                attempts++
            }

            assertEquals(initialUserCount + 3, finalUserCount,
                "Expected user count to increase by 3, but it was $initialUserCount before and $finalUserCount after after $attempts attempts")

            val allUsers = userStorageService.getAllUsers()
            assertTrue(allUsers.any { it.email == "bruce.lee@mail.com" },
                "User with email bruce.lee@mail.com should be present")
            assertTrue(allUsers.any { it.email == "anna.lee@mail.com" },
                "User with email anna.lee@mail.com should be present")
            assertTrue(allUsers.any { it.email == "bob.lee@mail.com" },
                "User with email bob.lee@mail.com should be present")
        }
    }

    @Test
    fun `should reject non-CSV file`() = testApplication {
        val eventBus = EventBus()
        val fileStorageService = FileStorageService("test-uploads")
        val userStorageService = UserStorageService()
        val processingErrorService = ProcessingErrorService()

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(CORS) {
                anyHost()
            }
            configureRouting(
                eventBus = eventBus,
                fileStorageService = fileStorageService,
                userStorageService = userStorageService,
                processingErrorService = processingErrorService
            )
        }

        val textFile = createTempFile("test.txt", "This is not a CSV file")

        val response = client.post("/api/files/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", textFile.toFile().readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"test.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should return user count via API`() = testApplication {
        val eventBus = EventBus()
        val fileStorageService = FileStorageService("test-uploads")
        val csvProcessingService = CsvProcessingService()
        val processingErrorService = ProcessingErrorService()
        val userStorageService = UserStorageService()

        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("single-user.csv", csvContent)

        runBlocking {
            csvProcessingService.processCsvFile(csvFile) { batch ->
                userStorageService.saveBatch(batch)
            }
        }

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(CORS) {
                anyHost()
            }
            configureRouting(
                eventBus = eventBus,
                fileStorageService = fileStorageService,
                userStorageService = userStorageService,
                processingErrorService = processingErrorService
            )
        }

        val response = client.get("/api/files/users/count")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("count"))
    }

    @Test
    fun `should return all users via API`() = testApplication {
        val eventBus = EventBus()
        val fileStorageService = FileStorageService("test-uploads")
        val csvProcessingService = CsvProcessingService()
        val processingErrorService = ProcessingErrorService()
        val userStorageService = UserStorageService()

        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
            2,Anna,lee,anna.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("users-api.csv", csvContent)

        runBlocking {
            csvProcessingService.processCsvFile(csvFile) { batch ->
                userStorageService.saveBatch(batch)
            }
        }

        application {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(CORS) {
                anyHost()
            }
            configureRouting(
                eventBus = eventBus,
                fileStorageService = fileStorageService,
                userStorageService = userStorageService,
                processingErrorService = processingErrorService
            )
        }

        val response = client.get("/api/files/users")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("count"))
        assertTrue(body.contains("users"))
    }

    private fun createTempCsvFile(fileName: String, content: String): Path {
        val testDir = Path.of("test-uploads")
        Files.createDirectories(testDir)
        val file = testDir.resolve(fileName)
        Files.write(file, content.toByteArray())
        return file
    }

    private fun createTempFile(fileName: String, content: String): Path {
        val testDir = Path.of("test-uploads")
        Files.createDirectories(testDir)
        val file = testDir.resolve(fileName)
        Files.write(file, content.toByteArray())
        return file
    }
}
