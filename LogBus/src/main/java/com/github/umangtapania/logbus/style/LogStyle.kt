package com.github.umangtapania.logbus.style

import com.github.umangtapania.logbus.core.LogLevel

/**
 * Interface defining the contract for log message formatting.
 * 
 * This interface provides a way to customize how log messages are formatted
 * before they are displayed or stored. Implementations can add styling,
 * emojis, or other formatting elements based on the log level.
 */
interface LogStyle {
    /**
     * Formats a log message according to the specified log level.
     *
     * @param message The original log message to be formatted
     * @param level The [LogLevel] of the message, which can be used to determine formatting
     * @return The formatted message string
     */
    fun format(message : String, level : LogLevel) : String{
        return message
    }
}