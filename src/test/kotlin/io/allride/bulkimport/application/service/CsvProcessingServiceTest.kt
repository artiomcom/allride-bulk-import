package io.allride.bulkimport.application.service

import io.allride.bulkimport.domain.model.User
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CsvProcessingServiceTest {

    private lateinit var csvProcessingService: CsvProcessingService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        csvProcessingService = CsvProcessingService()
    }

    @Test
    fun `should process valid CSV file successfully`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
            2,Anna,lee,anna.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("valid.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(2, result.processedCount)
        assertEquals(0, result.errors.size)
        assertEquals(1, collectedBatches.size)
        assertEquals(2, collectedBatches[0].size)

        val users = collectedBatches[0]
        assertEquals("1", users[0].id)
        assertEquals("Bruce", users[0].firstName)
        assertEquals("lee", users[0].lastName)
        assertEquals("bruce.lee@mail.com", users[0].email)
    }

    @Test
    fun `should handle empty CSV file`() = runBlocking {
        val csvContent = "id,firstName,lastName,email"
        val csvFile = createTempCsvFile("empty.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(0, result.processedCount)
        assertEquals(0, result.errors.size)
        assertEquals(0, collectedBatches.size)
    }

    @Test
    fun `should skip rows with insufficient columns`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,John,Doe
            2,Anna,lee,anna.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("insufficient.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(1, result.processedCount)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].message.contains("Insufficient columns"))
        assertEquals(1, collectedBatches.size)
        assertEquals(1, collectedBatches[0].size)
    }

    @Test
    fun `should reject rows with missing required fields`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,,Doe,bruce.lee@mail.com
            2,Jane,,anna.lee@mail.com
            3,Bob,Johnson,
        """.trimIndent()
        val csvFile = createTempCsvFile("missing.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(0, result.processedCount)
        assertEquals(3, result.errors.size)
        assertTrue(result.errors.all { it.message.contains("Missing required fields") })
        assertEquals(0, collectedBatches.size)
    }

    @Test
    fun `should reject rows with invalid email format`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,Lee,invalid-email
            2,Anna,Lee,jane@mail.com
            3,Bob,Lee,not-an-email
        """.trimIndent()
        val csvFile = createTempCsvFile("invalid-email.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(1, result.processedCount)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.all { it.message.contains("Invalid email format") })
        assertEquals(1, collectedBatches.size)
        assertEquals("jane@mail.com", collectedBatches[0][0].email)
    }

    @Test
    fun `should handle mixed valid and invalid rows`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
            2,,Lee,anna.lee@mail.com
            3,Bob,Lee,invalid-email
            4,Alice,Lee,alice.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("mixed.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(2, result.processedCount)
        assertEquals(2, result.errors.size)
        assertEquals(1, collectedBatches.size)
        assertEquals(2, collectedBatches[0].size)
    }

    @Test
    fun `should trim whitespace from fields`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,  John  ,  Doe  ,  bruce.lee@mail.com  
        """.trimIndent()
        val csvFile = createTempCsvFile("whitespace.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(1, result.processedCount)
        assertEquals(1, collectedBatches.size)
        val user = collectedBatches[0][0]
        assertEquals("John", user.firstName)
        assertEquals("Doe", user.lastName)
        assertEquals("bruce.lee@mail.com", user.email)
    }

    @Test
    fun `should handle non-existent file gracefully`() = runBlocking {
        val nonExistentFile = tempDir.resolve("non-existent.csv")

        val collectedBatches = mutableListOf<List<User>>()

        val result = csvProcessingService.processCsvFile(nonExistentFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(0, result.processedCount)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors[0].message.contains("Failed to read CSV file"))
        assertEquals(0, collectedBatches.size)
    }

    @Test
    fun `should collect all users in batches`() = runBlocking {
        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
            2,Anna,lee,anna.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("users.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()
        val allUsers = mutableListOf<User>()

        csvProcessingService.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
            allUsers.addAll(batch)
        }

        assertEquals(1, collectedBatches.size)
        assertEquals(2, allUsers.size)
        assertEquals("Bruce", allUsers[0].firstName)
        assertEquals("Anna", allUsers[1].firstName)
    }

    @Test
    fun `should process multiple batches when batch size is reached`() = runBlocking {
        val service = CsvProcessingService(batchSize = 2, maxErrors = 100)

        val csvContent = """
            id,firstName,lastName,email
            1,Bruce,lee,bruce.lee@mail.com
            2,Anna,lee,anna.lee@mail.com
            3,Bob,Lee,bob.lee@mail.com
        """.trimIndent()
        val csvFile = createTempCsvFile("count.csv", csvContent)

        val collectedBatches = mutableListOf<List<User>>()

        val result = service.processCsvFile(csvFile) { batch ->
            collectedBatches.add(batch)
        }

        assertEquals(3, result.processedCount)
        assertEquals(2, collectedBatches.size)
        assertEquals(2, collectedBatches[0].size)
        assertEquals(1, collectedBatches[1].size)
    }

    private fun createTempCsvFile(fileName: String, content: String): Path {
        val file = tempDir.resolve(fileName).toFile()
        file.writeText(content)
        return file.toPath()
    }
}

