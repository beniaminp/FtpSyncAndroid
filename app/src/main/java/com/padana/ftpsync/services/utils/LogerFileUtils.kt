package com.padana.ftpsync.services.utils

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*


object LogerFileUtils {
    val dirName = "/sdcard/sync-app/"
    val format = SimpleDateFormat("dd_MM_yyy")
    val dateString = format.format(Date())
    val loggerName = "logger_" + dateString + ".txt"

    fun error(logString: String) {
        val file = File(dirName, loggerName)
        if (!file.exists()) {
            File(dirName).mkdirs()
            file.createNewFile()
        }
        val fileWriter = FileWriter(file, true) //Set true for append mode

        val printWriter = PrintWriter(fileWriter)
        printWriter.println("ERROR --- " + Date().toString() + " --- " + logString)
        printWriter.close()
    }

    fun info(logString: String) {
        val file = File(dirName, loggerName)
        if (!file.exists()) {
            File(dirName).mkdirs()
            file.createNewFile()
        }
        val fileWriter = FileWriter(file, true) //Set true for append mode

        val printWriter = PrintWriter(fileWriter)
        printWriter.println("INFO ---" + Date().toString() + " --- " + logString)
        printWriter.close()
    }
}