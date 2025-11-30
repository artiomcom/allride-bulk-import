package io.allride.bulkimport.domain.event

import kotlinx.serialization.Serializable

@Serializable
data class FileUploadedEvent(
    val filePath: String,
    val fileName: String
)

