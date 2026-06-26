package com.aetherhunt.game.core.utils

import android.content.Context
import com.google.gson.Gson
import java.io.File

class SaveManager(private val context: Context) {
    private val gson = Gson()
    private val mainFile = File(context.filesDir, "aether_save.json")
    private val tempFile = File(context.filesDir, "aether_save.tmp")
    private val backupFile = File(context.filesDir, "aether_save.bak")

    fun saveInstantly(state: Any) {
        try {
            val json = gson.toJson(state)
            tempFile.writeText(json)
            if (mainFile.exists()) mainFile.copyTo(backupFile, overwrite = true)
            // Atomic rename at the kernel level. Survives instant app termination.
            tempFile.renameTo(mainFile) 
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun <T> loadState(clazz: Class<T>): T? {
        return try {
            if (mainFile.exists()) gson.fromJson(mainFile.readText(), clazz)
            else null
        } catch (e: Exception) {
            // Fallback to backup if main was corrupted mid-write
            if (backupFile.exists()) gson.fromJson(backupFile.readText(), clazz) else null
        }
    }

