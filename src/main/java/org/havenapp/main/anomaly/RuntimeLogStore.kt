package org.havenapp.main.anomaly

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayDeque

object RuntimeLogStore {
    enum class Level { DEBUG, INFO, WARNING, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    private const val MAX_ENTRIES = 1000
    private val entries = ArrayDeque<Entry>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(level: Level, tag: String, message: String) {
        entries.addLast(Entry(System.currentTimeMillis(), level, tag, message))
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    fun debug(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(Level.INFO, tag, message)
    fun warning(tag: String, message: String) = log(Level.WARNING, tag, message)
    fun error(tag: String, message: String) = log(Level.ERROR, tag, message)

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun format(entry: Entry): String =
            "${formatter.format(Date(entry.timestamp))} ${entry.level} ${entry.tag}: ${entry.message}"
}
