package com.github.umangtapania.logbus.internal

import android.content.Context
import java.io.File
import java.io.FileWriter

object FileUtils {

    fun writeToFile(context: Context, fileName: String, message: String) {
        val file = File(context.getExternalFilesDir(null), fileName)
        if (!file.exists()) file.createNewFile()
        FileWriter(file, true).use {
            it.append(message).append("\n")
        }
    }
}