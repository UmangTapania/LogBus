package com.github.umangtapania.logbus.route

import android.content.Context
import android.util.Log
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.internal.FileUtils.writeToFile
import com.github.umangtapania.logbus.internal.LogConstants

class CrashlyticsRoute(private val context : Context) : LogRoute{

    init {
        setupCrashCatcher()
    }

    override fun log(level: LogLevel, tag: String, message: String) {
        if (level == LogLevel.ERROR){
//            write to log file for crashlytics
            writeToFile(context, LogConstants.CRASHLYTICS_FILENAME, message)
        }
    }

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
            log(LogLevel.ERROR, crashInfo, crashInfo)

            // Let the crash happen
//            we will check if we can stop the crash
            System.exit(1)
        }
    }
}