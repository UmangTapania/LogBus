package com.github.umangtapania.logbus.style

/**
 * Enumeration defining different box styles for log message formatting.
 * 
 * These styles determine how log messages are visually contained and presented:
 * - [SINGLE_BORDER] uses single-line characters (┌─┐│└┘)
 * - [DOUBLE_BORDER] uses double-line characters (╔═╗║╚╝)
 * - [NONE] displays the message without any box formatting
 */
enum class LogBoxStyle {
    SINGLE_BORDER, DOUBLE_BORDER, NONE
}