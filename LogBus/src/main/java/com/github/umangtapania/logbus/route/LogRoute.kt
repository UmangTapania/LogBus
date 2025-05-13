package com.github.umangtapania.logbus.route

import com.github.umangtapania.logbus.core.LogLevel

/**
 * Base interface for all logging routes in LogBus.
 * 
 * This interface defines the contract for different logging destinations (like Logcat, File, Crashlytics).
 * Each implementation should handle the actual logging mechanism according to its specific requirements.
 */
interface LogRoute {
    /**
     * Logs a message with the specified level and tag.
     *
     * @param level The [LogLevel] indicating the severity of the log message
     * @param tag A string identifier for the log message
     * @param message The actual log message content
     */
    suspend fun log(level: LogLevel, tag: String, message: String)
}