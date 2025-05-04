package com.github.umangtapania.logbus.extensions

import com.github.umangtapania.logbus.core.LogLevel


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