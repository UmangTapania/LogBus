package com.github.umangtapania.logbus.route

import com.github.umangtapania.logbus.core.LogLevel

interface LogRoute {
    fun log(level: LogLevel, tag: String, message: String)
}