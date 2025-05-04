package com.github.umangtapania.logbus.style

interface LogStyle {
    fun format(message : String) : String{
        return message
    }
}