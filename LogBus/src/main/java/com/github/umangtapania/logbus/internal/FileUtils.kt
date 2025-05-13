package com.github.umangtapania.logbus.internal

import android.content.Context
import java.io.File
import java.io.FileWriter

/**
 * Utility object for file operations used by LogBus.
 *
 * This object provides helper methods for file-based operations like:
 * - Writing log messages to files
 * - Managing log files in the app's external storage
 *
 * All operations are performed in the app's external files directory
 * which doesn't require additional storage permissions.
 */
object FileUtils {

/**
 * Writes a message to a file in the app's external storage directory.
 *
 * @param context The Android context used to get the external files directory
 * @param fileName Name of the file to write to
 * @param message The message content to be written to the file
 * 
 * The message will be appended to the file with a newline character.
 * If the file doesn't exist, it will be created automatically.
 */
    fun writeToFile(context: Context, fileName: String, message: String) {
        val file = File(context.getExternalFilesDir(null), fileName)
        if (!file.exists()) file.createNewFile()
        FileWriter(file, true).use {
            it.append(message).append("\n")
        }
    }
}