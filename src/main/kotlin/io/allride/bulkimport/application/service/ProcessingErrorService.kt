package io.allride.bulkimport.application.service

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ProcessingErrorService {
    private val errorsByFile: MutableMap<String, FileProcessingErrors> = ConcurrentHashMap()

    fun saveErrors(fileName: String, errors: List<String>, processedCount: Int, totalRows: Int) {
        errorsByFile[fileName] = FileProcessingErrors(
            fileName = fileName,
            errors = errors,
            processedCount = processedCount,
            totalRows = totalRows,
            timestamp = Instant.now()
        )
    }

    fun getErrors(fileName: String): FileProcessingErrors? {
        return errorsByFile[fileName]
    }

    fun getLatestErrors(): FileProcessingErrors? {
        return errorsByFile.values.maxByOrNull { it.timestamp }
    }
}

data class FileProcessingErrors(
    val fileName: String,
    val errors: List<String>,
    val processedCount: Int,
    val totalRows: Int,
    val timestamp: Instant
)

