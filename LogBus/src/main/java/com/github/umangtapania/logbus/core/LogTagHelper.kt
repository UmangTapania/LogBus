package com.github.umangtapania.logbus.core

object LogTagHelper {
    /**
     * Generates a log tag by getting the name of the Activity that is trying to send the log.
     * 
     * This method walks up the stack trace to find the first Activity class that called the logging method.
     * If no Activity is found in the stack trace, it returns "LogBus" as a fallback.
     *
     * @return The simple name of the calling Activity class, or "LogBus" if no Activity is found
     */
    fun generateLogTag() : String {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            try {
                val className = Class.forName(element.className)
                if (android.app.Activity::class.java.isAssignableFrom(className)) {
                    return className.simpleName
                }
            } catch (e: ClassNotFoundException) {
                continue
            }
        }
        return "LogBus"
    }
}