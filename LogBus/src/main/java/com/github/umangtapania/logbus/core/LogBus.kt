package com.github.umangtapania.logbus.core

import android.util.Log
import com.github.umangtapania.logbus.route.LogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LogBus is a flexible logging library for Android that allows routing logs to multiple destinations.
 * 
 * It provides:
 * - Multiple log levels (VERBOSE, DEBUG, INFO, WARNING, ERROR)
 * - Support for multiple logging destinations through [LogRoute] implementations
 * - Built-in routes for Logcat, File logging, and Crashlytics
 * - Customizable log formatting and styling
 * - Coroutine-based asynchronous logging
 *
 * Usage:
 * ```
 * // Initialize with desired routes
 * LogBus.initialize(listOf(
 *     LogcatRoute(),
 *     FileRoute(context),
 *     CrashlyticsRoute(context)
 * ))
 *
 * // Log messages using convenience methods
 * LogBus.d("Debug message")
 * LogBus.i("Info message", "CustomTag")
 * LogBus.e("Error message")
 * ```
 *
 * The library must be initialized with [initialize] before use. Attempting to log messages before
 * initialization will result in logs being dropped.
 */
object LogBus {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val routes = mutableListOf<LogRoute>()
    private var isInitialized = false

    fun initialize(routes: List<LogRoute>) {
        if (isInitialized) {
            throw IllegalStateException("LogBus is already initialized")
        }
        this.routes.addAll(routes)
        isInitialized = true
    }

    /**
     * Logs a message with VERBOSE level
     * @param message The message to be logged
     * @param tag Optional tag to identify the source of the log. If null, a tag will be automatically generated
     */
    fun a(message: String, tag: String? = null) = log(LogLevel.VERBOSE, tag, message)

    /**
     * Logs a message with DEBUG level
     * @param message The message to be logged
     * @param tag Optional tag to identify the source of the log. If null, a tag will be automatically generated
     */
    fun d(message: String, tag: String? = null) = log(LogLevel.DEBUG, tag, message)

    /**
     * Logs a message with INFO level
     * @param message The message to be logged
     * @param tag Optional tag to identify the source of the log. If null, a tag will be automatically generated
     */
    fun i(message: String, tag: String? = null) = log(LogLevel.INFO, tag, message)

    /**
     * Logs a message with WARNING level
     * @param message The message to be logged
     * @param tag Optional tag to identify the source of the log. If null, a tag will be automatically generated
     */
    fun w(message: String, tag: String? = null) = log(LogLevel.WARNING, tag, message)

    /**
     * Logs a message with ERROR level
     * @param message The message to be logged
     * @param tag Optional tag to identify the source of the log. If null, a tag will be automatically generated
     */
    fun e(message: String, tag: String? = null) = log(LogLevel.ERROR, tag, message)



/**
         * Logs a message to all registered routes with the specified log level.
         *
         * @param level The severity level of the log message ([LogLevel.VERBOSE], [LogLevel.DEBUG], etc.)
         * @param tag Optional tag to identify the source of the log. If null, a tag will be automatically generated
         * @param message The actual message to be logged
         * @throws IllegalStateException if LogBus is not initialized before use
         *
         * Example usage:
         * ```
         * // Initialize LogBus with routes
         * LogBus.initialize(listOf(LogcatRoute(), FileRoute(context)))
         *
         * // Log messages with different levels
         * LogBus.d("Debug message")  // Uses auto-generated tag
         * LogBus.i("Info message", "CustomTag")
         * LogBus.e("Error message")
         * ```
         *
         * The message will be dispatched asynchronously to all registered logging routes.
         * Each route handles the formatting and actual logging implementation independently.
         */
    private fun log(level: LogLevel, tag: String?, message: String) {
        if (!isInitialized) {
            throw IllegalStateException("LogBus must be initialized before use")
        }
        
        val finalTag = tag ?: LogTagHelper.generateLogTag()

        routes.forEach { route ->
            scope.launch {
                try {
                    route.log(level, finalTag, message)
                } catch (e: Exception) {
                    Log.e("LOG BUS", "Some error occurred")
                }
            }
        }
    }
}