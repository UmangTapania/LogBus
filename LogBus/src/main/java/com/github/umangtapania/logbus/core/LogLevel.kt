package com.github.umangtapania.logbus.core
/**
 * Represents different severity levels for logging messages.
 *
 * The available log levels are:
 * - [VERBOSE]: Detailed logs used for debugging, lowest priority
 * - [DEBUG]: Debug logs with more detail than INFO
 * - [INFO]: General information about app operation
 * - [WARNING]: Potentially harmful situations that aren't errors
 * - [ERROR]: Error conditions that should be addressed, highest priority
 *
 * Usage example:
 * ```
 * LogBus.log(LogLevel.DEBUG, "MyTag", "Debug message")
 * ```
 */
enum class LogLevel {
    VERBOSE, INFO, DEBUG, WARNING, ERROR
}