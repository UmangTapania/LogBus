package com.github.umangtapania.logbus.route

import android.content.Context
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.internal.FileUtils.writeToFile
import com.github.umangtapania.logbus.internal.LogConstants

class FileRoute(private val context : Context) : LogRoute {
    override fun log(level: LogLevel, tag: String, message: String) {
        writeToFile(context, LogConstants.FILE_STORAGE_FILE_NAME, message)
    }
}