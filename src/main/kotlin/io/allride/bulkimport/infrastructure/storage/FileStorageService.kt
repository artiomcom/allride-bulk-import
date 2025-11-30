package io.allride.bulkimport.infrastructure.storage

import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

class FileStorageService(
    uploadDir: String
) {
    private val uploadPath: Path = Paths.get(uploadDir).toAbsolutePath().normalize()

    init {
        Files.createDirectories(uploadPath)
    }

    fun storeFile(part: PartData.FileItem): String {
        val fileName = "${UUID.randomUUID()}_${part.originalFileName}"
        val targetLocation = uploadPath.resolve(fileName)

        part.streamProvider().use { inputStream ->
            Files.copy(inputStream, targetLocation)
        }

        return targetLocation.toString()
    }

    fun getFile(filePath: String): Path =
        Paths.get(filePath)
}
