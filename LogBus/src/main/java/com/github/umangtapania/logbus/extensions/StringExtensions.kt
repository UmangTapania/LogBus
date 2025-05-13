package com.github.umangtapania.logbus.extensions

import com.github.umangtapania.logbus.core.LogLevel

/**
 * Contains extension functions for String to enhance logging functionality.
 *
 * This file provides extensions that add visual enhancements to log messages,
 * such as adding level-specific emojis to make logs more readable and distinguishable.
 *
 * Example usage:
 * ```
 * val message = "Debug info"
 * val enhancedMessage = message.addLogEmoji(LogLevel.DEBUG) // Returns "🐞 Debug info"
 * ```
 */
fun String.addLogEmoji(level: LogLevel):String{
    val emoji = when(level){
        LogLevel.VERBOSE -> "🔍"
        LogLevel.INFO -> "ℹ️"
        LogLevel.DEBUG -> "🐞"
        LogLevel.WARNING -> "⚠️"
        LogLevel.ERROR -> "❌"
    }
    return "$emoji $this"
}