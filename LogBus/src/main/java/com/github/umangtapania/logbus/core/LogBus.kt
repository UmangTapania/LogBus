package com.github.umangtapania.logbus.core

import com.github.umangtapania.logbus.route.LogRoute

object LogBus {
    private val routes = mutableListOf<LogRoute>()

    fun addRoute(route: LogRoute) {
        routes.add(route)
    }

    fun a(message: String, tag: String? = null) = log(LogLevel.VERBOSE, tag, message)
    fun d(message: String, tag: String? = null) = log(LogLevel.DEBUG, tag, message)
    fun i(message: String, tag: String? = null) = log(LogLevel.INFO, tag, message)
    fun w(message: String, tag: String? = null) = log(LogLevel.WARNING, tag, message)
    fun e(message: String, tag: String? = null) = log(LogLevel.ERROR, tag, message)

    private fun log(level: LogLevel, tag: String?, message: String) {
        for (route in routes) {
            route.log(level, tag ?: LogTagHelper.generateLogTag(), message)
        }
    }
}