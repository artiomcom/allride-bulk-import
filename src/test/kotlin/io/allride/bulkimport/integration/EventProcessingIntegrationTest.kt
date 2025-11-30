package io.allride.bulkimport.integration

import io.allride.bulkimport.application.service.CsvProcessingService
import io.allride.bulkimport.application.service.ProcessingErrorService
import io.allride.bulkimport.application.service.UserStorageService
import io.allride.bulkimport.application.worker.CsvProcessingWorker
import io.allride.bulkimport.domain.event.UsersBatchEvent
import io.allride.bulkimport.domain.event.FileUploadedEvent
import io.allride.bulkimport.infrastructure.messaging.EventBus
import io.allride.bulkimport.infrastructure.storage.FileStorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EventProcessingIntegrationTest {

    private lateinit var eventBus: EventBus
    private lateinit var fileStorageService: FileStorageService
    private lateinit var csvProcessingService: CsvProcessingService
    private lateinit var processingErrorService: ProcessingErrorService
    private lateinit var userStorageService: UserStorageService
    private lateinit var worker: CsvProcessingWorker

    @BeforeEach
    fun setUp() {
        eventBus = EventBus()
        fileStorageService = FileStorageService("test-uploads")
        csvProcessingService = CsvProcessingService()
        processingErrorService = ProcessingErrorService()
        userStorageService = UserStorageService()

        worker = CsvProcessingWorker(
            eventBus = eventBus,
            fileStorageService = fileStorageService,
            csvProcessingService = csvProcessingService,
            processingErrorService = processingErrorService
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            worker.start()
        }

        scope.launch {
            eventBus.subscribeBatches()
                .onEach { batchEvent ->
                    userStorageService.saveBatch(batchEvent.users)
                }
                .launchIn(scope)
        }

        val testUploadsDir = Path.of("test-uploads")
        if (Files.exists(testUploadsDir)) {
            Files.walk(testUploadsDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `should process event and parse CSV file`() = runBlocking {
        val initialUserCount = userStorageService.getUserCount()
        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
            2,Anna,lee,anna.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("event-test.csv", csvContent)

        val event = FileUploadedEvent(
            filePath = csvFile.toString(),
            fileName = "event-test.csv"
        )

        eventBus.publish(event)

        var finalUserCount = userStorageService.getUserCount()
        var attempts = 0
        val maxAttempts = 10
        while (finalUserCount < initialUserCount + 2 && attempts < maxAttempts) {
            delay(500)
            finalUserCount = userStorageService.getUserCount()
            attempts++
        }

        assertEquals(initialUserCount + 2, finalUserCount,
            "Expected user count to increase by 2, but it was $initialUserCount before and $finalUserCount after after $attempts attempts")

        val users = userStorageService.getAllUsers()
        assertTrue(users.any { it.email == "bruce.lee@mail.com" },
            "User with email bruce.lee@mail.com should be present")
        assertTrue(users.any { it.email == "anna.lee@mail.com" },
            "User with email anna.lee@mail.com should be present")
    }

    @Test
    fun `should handle multiple events sequentially`() = runBlocking {
        val initialUserCount = userStorageService.getUserCount()
        val csvContent1 = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
        """.trimIndent()
        val csvFile1 = createTempCsvFile("event1.csv", csvContent1)

        val csvContent2 = """
            id,firstName,lastName,email
            2,Anna,lee,anna.lee@mail.com
        """.trimIndent()
        val csvFile2 = createTempCsvFile("event2.csv", csvContent2)

        val event1 = FileUploadedEvent(csvFile1.toString(), "event1.csv")
        val event2 = FileUploadedEvent(csvFile2.toString(), "event2.csv")

        eventBus.publish(event1)
        delay(1000)
        eventBus.publish(event2)

        var finalUserCount = userStorageService.getUserCount()
        var attempts = 0
        val maxAttempts = 10
        while (finalUserCount < initialUserCount + 2 && attempts < maxAttempts) {
            delay(500)
            finalUserCount = userStorageService.getUserCount()
            attempts++
        }

        assertEquals(initialUserCount + 2, finalUserCount,
            "Expected user count to increase by 2, but it was $initialUserCount before and $finalUserCount after after $attempts attempts")
    }

    private fun createTempCsvFile(fileName: String, content: String): Path {
        val testDir = Path.of("test-uploads")
        Files.createDirectories(testDir)
        val file = testDir.resolve(fileName)
        Files.write(file, content.toByteArray())
        return file
    }
}
