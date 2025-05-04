package com.github.umangtapania.logbus.config

import com.github.umangtapania.logbus.style.LogBoxStyle


data class LogStyleConfig(
    val box : LogBoxStyle,
    val useEmoji : Boolean,
    val wrapText : Boolean,
    val maxLineLength : Int = 75
)