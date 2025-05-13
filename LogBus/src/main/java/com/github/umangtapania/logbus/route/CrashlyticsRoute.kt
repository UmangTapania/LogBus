package com.github.umangtapania.logbus.route

import android.content.Context
import android.util.Log
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.internal.FileUtils.writeToFile
import com.github.umangtapania.logbus.internal.LogConstants

/**
 * A logging route specifically designed for crash reporting and error tracking.
 * 
 * This route handles both manual error logging and automatic crash detection.
 * It writes error logs to a dedicated file and sets up a global exception handler
 * to catch and log uncaught exceptions.
 *
 * @property context The Android [Context] used to access file storage
 */
class CrashlyticsRoute(private val context : Context) : LogRoute {

    init {
        setupCrashCatcher()
    }

    /**
     * Logs error messages to a dedicated crash reporting file.
     * Only messages with [LogLevel.ERROR] are processed.
     *
     * @param level The [LogLevel] of the message (only ERROR level is processed)
     * @param tag The tag associated with the error message
     * @param message The error message to be logged
     */
    override suspend fun log(level: LogLevel, tag: String, message: String) {
        if (level == LogLevel.ERROR){
//            write to log file for crashlytics
            writeToFile(context, LogConstants.CRASHLYTICS_FILENAME, message)
        }
    }

    /**
     * Sets up a global exception handler to catch and log uncaught exceptions.
     * When a crash occurs, it:
     * 1. Captures detailed crash information including thread name, exception type,
     *    message, and stack trace
     * 2. Formats the crash information with emoji indicators
     * 3. Logs the crash information
     * 4. Terminates the application
     */
    private fun setupCrashCatcher() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashInfo = """
                🚨 CRASH DETECTED 🚨
                Thread: ${thread.name}
                Exception: ${throwable::class.java.name}
                Message: ${throwable.message}
                Stacktrace: ${Log.getStackTraceString(throwable)}
            """.trimIndent()

            // Send it through LogBus
//            log(LogLevel.ERROR, crashInfo, crashInfo)

            // Let the crash happen
//            we will check if we can stop the crash
            System.exit(1)
        }
    }
}