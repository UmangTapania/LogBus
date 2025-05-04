package com.github.umangtapania.logbus.style

import com.github.umangtapania.logbus.config.LogStyleConfig
import com.github.umangtapania.logbus.internal.LogFormatter


class LogcatStyle(
    private val config : LogStyleConfig = LogStyleConfig(
        box = LogBoxStyle.DOUBLE_BORDER,
        useEmoji = true,
        wrapText = true
    )
) : LogStyle {

    override fun format(message: String): String {
        return LogFormatter.wrapMessageInBox(message, config)
    }

}