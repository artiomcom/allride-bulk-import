package io.allride.bulkimport.application.service

import io.allride.bulkimport.domain.model.User
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for storing and retrieving users.
 * In production, this would use a database instead of in-memory storage.
 * 
 * For large-scale systems, this service would subscribe to UsersBatchEvent
 * and save batches to database instead of keeping them in memory.
 */
class UserStorageService {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val users: MutableMap<String, User> = ConcurrentHashMap()
    private val userCount = AtomicInteger(0)
    
    /**
     * Save a batch of users.
     * In production, this would perform batch INSERT to database.
     */
    fun saveBatch(batch: List<User>) {
        batch.forEach { user ->
            users[user.id] = user
        }
        userCount.addAndGet(batch.size)
        logger.debug("Saved batch of {} users. Total users: {}", batch.size, userCount.get())
    }

    /**
     * Save a single user.
     */
    fun saveUser(user: User) {
        if (users.putIfAbsent(user.id, user) == null) {
            userCount.incrementAndGet()
        }
    }
    
    /**
     * Get all users.
     * WARNING: For large datasets, this will cause OOM.
     * In production, use pagination or database queries.
     */
    fun getAllUsers(): List<User> {
        return users.values.toList()
    }
    
    /**
     * Get total number of users.
     */
    fun getUserCount(): Int {
        return userCount.get()
    }
    
    /**
     * Clear all users (useful for testing).
     */
    fun clear() {
        users.clear()
        userCount.set(0)
    }
}

