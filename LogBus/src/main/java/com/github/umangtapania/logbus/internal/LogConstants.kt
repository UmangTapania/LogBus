package com.github.umangtapania.logbus.internal
/**
 * Constants used throughout the LogBus library.
 *
 * This object contains constant values for:
 * - Default filenames for different logging destinations
 * - Box drawing characters for log message formatting
 *
 * These constants ensure consistency across the library and make configuration
 * changes easier to manage from a central location.
 */
object LogConstants{
    const val CRASHLYTICS_FILENAME = "LogBus_Crashlytics"
    const val FILE_STORAGE_FILE_NAME = "LogBus_logs"

    const val SINGLE_BOX_BORDER = "─"
    const val DOUBLE_BOX_BORDER = "═"
}