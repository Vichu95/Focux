package com.focux.focux

import android.content.Context
import com.focux.focux.db.AppDatabase
import com.focux.focux.db.LogEvent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogWriter {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Appends a line to the log database in the background.
     */
    fun append(context: Context, line: String) {
        val dao = AppDatabase.getDatabase(context).logEventDao()
        // Use GlobalScope for fire-and-forget logging from anywhere.
        GlobalScope.launch {
            dao.insert(LogEvent(eventData = line))
        }
    }

    /**
     * Reads the entire log from the database, blocking the current thread.
     */
    fun read(context: Context): String {
        val dao = AppDatabase.getDatabase(context).logEventDao()
        // runBlocking is used here because the caller (MainActivity) expects a synchronous String result.
        // This is acceptable for this prototype but should be replaced with a proper ViewModel/Flow later.
        val events = runBlocking { dao.getAll() }
        return events.joinToString("\n") { "${sdf.format(Date(it.timestamp))} - ${it.eventData}" }
    }

    /**
     * Clears the entire log from the database, blocking the current thread.
     */
    fun clear(context: Context) {
        val dao = AppDatabase.getDatabase(context).logEventDao()
        runBlocking { dao.clear() }
    }
}
