package com.github.umangtapania.logbus.style

import com.github.umangtapania.logbus.config.LogStyleConfig
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.extensions.addLogEmoji
import com.github.umangtapania.logbus.internal.LogFormatter

/**
 * Default implementation of [LogStyle] for Logcat output formatting.
 * 
 * This style provides enhanced formatting for log messages displayed in Logcat,
 * including box borders, emoji indicators, and text wrapping capabilities.
 *
 * @property config The [LogStyleConfig] containing formatting preferences.
 *                 Defaults to double border, emoji enabled, and text wrapping enabled.
 */
class LogcatStyle(
    private val config : LogStyleConfig = LogStyleConfig(
        box = LogBoxStyle.DOUBLE_BORDER,
        useEmoji = true,
        wrapText = true
    )
) : LogStyle {

    /**
     * Formats a log message for Logcat display.
     * 
     * The formatting process:
     * 1. Optionally adds emoji indicators based on log level
     * 2. Wraps the message in a decorative box
     * 3. Applies text wrapping if enabled
     *
     * @param message The original log message to be formatted
     * @param level The [LogLevel] of the message, used for emoji selection
     * @return The formatted message string ready for Logcat display
     */
    override fun format(message: String, level : LogLevel): String {
        return LogFormatter.wrapMessageInBox(if (config.useEmoji) message.addLogEmoji(level) else message, config)
    }

}