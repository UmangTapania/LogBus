package com.github.umangtapania.logbus.route

import android.util.Log
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.style.LogStyle
import com.github.umangtapania.logbus.style.LogcatStyle

/**
 * A logging route that outputs messages to Android's Logcat.
 * 
 * This route formats and displays log messages in the Android system log,
 * with support for different log levels and custom styling.
 *
 * @property style The [LogStyle] to be used for formatting log messages.
 *                Defaults to [LogcatStyle] if not specified.
 */
class LogcatRoute(private val style: LogStyle = LogcatStyle()) : LogRoute {
    /**
     * Logs a message to Logcat with the specified level and tag.
     * The message is formatted according to the configured style before logging.
     *
     * @param level The [LogLevel] determining the type of log entry (DEBUG, INFO, ERROR, etc.)
     * @param tag The tag to identify the source of the log message
     * @param message The message to be logged, which will be formatted according to the style
     */
    override suspend fun log(level: LogLevel, tag: String, message: String) {
        val formattedMsg = style.format(message, level)
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, formattedMsg)
            LogLevel.INFO -> Log.i(tag, formattedMsg)
            LogLevel.ERROR -> Log.e(tag, formattedMsg)
            LogLevel.VERBOSE -> Log.v(tag, formattedMsg)
            LogLevel.WARNING -> Log.w(tag, formattedMsg)
        }
    }
}