package io.allride.bulkimport.infrastructure.storage

import io.ktor.http.ContentDisposition
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileStorageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var fileStorageService: FileStorageService

    @BeforeEach
    fun setUp() {
        fileStorageService = FileStorageService(tempDir.toString())
    }

    private fun createFileItem(
        fileName: String,
        content: ByteArray
    ): PartData.FileItem {
        val disposition = ContentDisposition.File
            .withParameter(ContentDisposition.Parameters.FileName, fileName)

        val headers = Headers.build {
            append(HttpHeaders.ContentDisposition, disposition.toString())
        }

        val provider: () -> Input = {
            buildPacket {
                writeFully(content)
            }
        }

        return PartData.FileItem(
            provider = provider,
            dispose = {},
            partHeaders = headers
        )
    }

    @Test
    fun `should store file successfully`() {
        val originalFileName = "test.csv"
        val fileContent = "test content".toByteArray()

        val fileItem = createFileItem(originalFileName, fileContent)

        val storedPath = fileStorageService.storeFile(fileItem)

        assertNotNull(storedPath)
        assertTrue(storedPath.contains(originalFileName))

        val file = Path.of(storedPath).toFile()
        assertTrue(file.exists())
        assertEquals(fileContent.size.toLong(), file.length())
        assertEquals("test content", file.readText())
    }

    @Test
    fun `should generate unique file names`() {
        val originalFileName = "test.csv"

        val content1 = "content1".toByteArray()
        val content2 = "content2".toByteArray()

        val fileItem1 = createFileItem(originalFileName, content1)
        val fileItem2 = createFileItem(originalFileName, content2)

        val path1 = fileStorageService.storeFile(fileItem1)
        val path2 = fileStorageService.storeFile(fileItem2)

        assertNotEquals(path1, path2)
        assertTrue(path1.contains(originalFileName))
        assertTrue(path2.contains(originalFileName))

        assertEquals("content1", Path.of(path1).toFile().readText())
        assertEquals("content2", Path.of(path2).toFile().readText())
    }

    @Test
    fun `should get file path correctly`() {
        val filePath = tempDir.resolve("test.csv").toString()
        Files.createFile(Path.of(filePath))

        val retrievedPath = fileStorageService.getFile(filePath)

        assertEquals(Path.of(filePath), retrievedPath)
        assertTrue(Files.exists(retrievedPath))
    }

    @Test
    fun `should create upload directory on initialization`() {
        val newUploadDir = tempDir.resolve("new-uploads")

        val service = FileStorageService(newUploadDir.toString())

        assertTrue(Files.exists(newUploadDir))
        assertTrue(Files.isDirectory(newUploadDir))
    }
}
