package com.github.umangtapania.logbus.route

import android.util.Log
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.style.LogStyle
import com.github.umangtapania.logbus.style.LogcatStyle

class LogcatRoute(private val style: LogStyle = LogcatStyle()) : LogRoute{
    override fun log(level: LogLevel, tag: String, message: String) {
        val formattedMsg = style.format(message)
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, formattedMsg)
            LogLevel.INFO -> Log.i(tag, formattedMsg)
            LogLevel.ERROR -> Log.e(tag, formattedMsg)
            LogLevel.VERBOSE -> Log.v(tag, formattedMsg)
            LogLevel.WARNING -> Log.w(tag, formattedMsg)
        }
    }
}