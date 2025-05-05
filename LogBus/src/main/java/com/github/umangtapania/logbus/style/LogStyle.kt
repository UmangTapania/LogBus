package com.github.umangtapania.logbus.style

import com.github.umangtapania.logbus.core.LogLevel

interface LogStyle {
    fun format(message : String, level : LogLevel) : String{
        return message
    }
}