package com.github.umangtapania.logbus.route

import android.content.Context
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.internal.FileUtils.writeToFile
import com.github.umangtapania.logbus.internal.LogConstants

/**
 * A logging route that writes log messages to a file.
 * 
 * This route stores all log messages in a file within the application's storage,
 * making it useful for persistent logging and debugging purposes.
 *
 * @property context The Android [Context] used to access file storage
 */
class FileRoute(private val context : Context) : LogRoute {
    /**
     * Writes the log message to a file.
     *
     * @param level The [LogLevel] of the message (not used in file logging)
     * @param tag The tag associated with the message (not used in file logging)
     * @param message The message to be written to the file
     */
    override suspend fun log(level: LogLevel, tag: String, message: String) {
        writeToFile(context, LogConstants.FILE_STORAGE_FILE_NAME, message)
    }
}