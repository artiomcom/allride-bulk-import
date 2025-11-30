package io.allride.bulkimport.application.service

import com.opencsv.CSVReaderBuilder
import io.allride.bulkimport.domain.model.User
import org.slf4j.LoggerFactory
import java.io.FileReader
import java.nio.file.Path

class CsvProcessingService(
    private val batchSize: Int = 10_000,
    private val maxErrors: Int = 1_000,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

    suspend fun processCsvFile(
        filePath: Path,
        onBatch: suspend (List<User>) -> Unit
    ): ProcessingResult {
        var processedCount = 0
        var totalRows = 0
        val errors = mutableListOf<RowError>()
        val batch = mutableListOf<User>()

        try {
            FileReader(filePath.toFile()).use { reader ->
                val csvReader = CSVReaderBuilder(reader)
                    .withSkipLines(1)
                    .build()

                var rowIndex = 1
                while (true) {
                    val record = csvReader.readNext() ?: break
                    rowIndex++
                    totalRows++

                    when (val result = parseRow(record, rowIndex)) {
                        is RowParsingResult.Ok -> {
                            batch.add(result.user)

                            if (batch.size >= batchSize) {
                                onBatch(batch.toList())
                                processedCount += batch.size
                                logger.debug(
                                    "Processed batch of {} users. Total processed: {}",
                                    batch.size,
                                    processedCount
                                )
                                batch.clear()
                            }
                        }
                        is RowParsingResult.Error -> {
                            addError(errors, result.error)
                            logger.debug("Row {} skipped: {}", rowIndex, result.error.message)
                        }
                    }
                }

                if (batch.isNotEmpty()) {
                    onBatch(batch.toList())
                    processedCount += batch.size
                    logger.debug("Processed last batch of {} users. Total processed: {}", batch.size, processedCount)
                    batch.clear()
                }
            }

            logger.info(
                "Processed {} users from {}. Errors: {}, Total rows (excluding header): {}",
                processedCount, filePath, errors.size, totalRows
            )

            return ProcessingResult(
                errors = errors.toList(),
                totalRows = totalRows,
                processedCount = processedCount
            )
        } catch (e: Exception) {
            logger.error("Error reading CSV file: $filePath", e)
            addError(
                errors,
                RowError(
                    rowIndex = 0,
                    message = "Failed to read CSV file: ${e.message ?: "unknown error"}"
                )
            )
            return ProcessingResult(
                errors = errors.toList(),
                totalRows = totalRows,
                processedCount = processedCount
            )
        }
    }

    private fun parseRow(record: Array<String>, rowIndex: Int): RowParsingResult {
        if (record.size < 4) {
            return RowParsingResult.Error(
                RowError(
                    rowIndex = rowIndex,
                    message = "Insufficient columns. Expected 4 (id, firstName, lastName, email), got ${record.size}"
                )
            )
        }

        val user = User(
            id = record[0].trim(),
            firstName = record[1].trim(),
            lastName = record[2].trim(),
            email = record[3].trim()
        )

        if (user.id.isBlank() || user.firstName.isBlank() ||
            user.lastName.isBlank() || user.email.isBlank()
        ) {
            return RowParsingResult.Error(
                RowError(rowIndex, "Missing required fields")
            )
        }

        if (!emailRegex.matches(user.email)) {
            val emailDisplay = if (user.email.length > 50) {
                "${user.email.take(50)}..."
            } else {
                user.email
            }
            return RowParsingResult.Error(
                RowError(rowIndex, "Invalid email format: $emailDisplay")
            )
        }

        return RowParsingResult.Ok(user)
    }


    private fun addError(errors: MutableList<RowError>, error: RowError) {
        if (errors.size < maxErrors) {
            errors.add(error)
        } else if (errors.size == maxErrors) {
            errors.add(
                RowError(
                    rowIndex = -1,
                    message = "... (error limit reached, more errors were encountered but not stored)"
                )
            )
        }
    }
}

sealed class RowParsingResult {
    data class Ok(val user: User) : RowParsingResult()
    data class Error(val error: RowError) : RowParsingResult()
}

data class RowError(
    val rowIndex: Int,
    val message: String
)

data class ProcessingResult(
    val errors: List<RowError>,
    val totalRows: Int,
    val processedCount: Int
)
