package com.github.umangtapania.logbus.internal

import com.github.umangtapania.logbus.config.LogStyleConfig
import com.github.umangtapania.logbus.style.LogBoxStyle

/**
 * A utility object that handles the formatting of log messages into visually appealing box layouts.
 * 
 * This formatter supports different box styles (single border, double border, or no border) and
 * can optionally include emoji decorations. It automatically wraps text to fit within specified
 * line length constraints while maintaining proper alignment and padding.
 */
object LogFormatter {

    /**
     * Wraps a message in a decorative box with specified styling.
     *
     * @param message The text message to be formatted and wrapped in a box
     * @param style The [LogStyleConfig] containing formatting preferences such as box style,
     *             maximum line length, and emoji usage
     * @return A formatted string containing the message wrapped in a box according to the specified style
     */
    fun wrapMessageInBox(message: String, style : LogStyleConfig): String {
        // Split the message into words
        val words = message.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.length + word.length + 1 <= style.maxLineLength) {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        val contentWidth = lines.maxOf { it.length }
        val boxed = StringBuilder()
        // Determine the width of the box
        when(style.box){
            LogBoxStyle.SINGLE_BORDER -> {
                val border = LogConstants.SINGLE_BOX_BORDER.repeat(contentWidth + if (style.useEmoji) 7 else 4)

                boxed.appendLine("┌$border┐")
                for (line in lines.withIndex()) {
                    val padding = " ".repeat(contentWidth - line.value.length)
                    boxed.appendLine(if (style.useEmoji){
                        if (line.index == 0) "│  ${line.value}$padding     │" else "│     ${line.value}$padding  │"
                    } else "│  ${line.value}$padding  │")
                }
                boxed.append("└$border┘")
            }
            LogBoxStyle.DOUBLE_BORDER -> {
                val border = LogConstants.DOUBLE_BOX_BORDER.repeat(contentWidth + if (style.useEmoji) 7 else 4)

                boxed.appendLine("╔$border╗")
                for (line in lines.withIndex()) {
                    val padding = " ".repeat(contentWidth - line.value.length)
                    boxed.appendLine(if (style.useEmoji){
                        if (line.index == 0) "║  ${line.value}$padding     ║" else "║     ${line.value}$padding  ║"
                    } else "║  ${line.value}$padding  ║")
                }
                boxed.append("╚$border╝")
            }
            LogBoxStyle.NONE -> {
                for (line in lines.withIndex()) {
                    boxed.appendLine(if (style.useEmoji){
                        if (line.index == 0) line.value else "   ${line.value}"
                    } else line.value)
                }
            }
        }

        return boxed.toString()
    }
}