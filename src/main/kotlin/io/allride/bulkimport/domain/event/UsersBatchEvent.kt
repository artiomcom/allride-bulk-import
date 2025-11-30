package io.allride.bulkimport.domain.event

import io.allride.bulkimport.domain.model.User
import kotlinx.serialization.Serializable

@Serializable
data class UsersBatchEvent(
    val users: List<User>,
    val sourceFileName: String,
    val batchNumber: Int
)

