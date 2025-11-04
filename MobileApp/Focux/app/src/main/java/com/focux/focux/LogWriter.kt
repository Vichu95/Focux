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

    fun append(context: Context, logEvent: LogEvent) {
        val dao = AppDatabase.getDatabase(context).logEventDao()
        GlobalScope.launch {
            dao.insert(logEvent)
        }
    }

    // This will be replaced by specific queries for the dashboard
    fun read(context: Context): String {
        val dao = AppDatabase.getDatabase(context).logEventDao()
        val events = runBlocking { dao.getAll() }
        return events.joinToString("\n") { event ->
            "${sdf.format(Date(event.timestamp))} | ${event.eventType} | ${event.eventAction} | ${event.packageName ?: "-"} | ${event.eventValue ?: "-"}"
        }
    }

    fun clear(context: Context) {
        val dao = AppDatabase.getDatabase(context).logEventDao()
        runBlocking { dao.clear() }
    }
}
