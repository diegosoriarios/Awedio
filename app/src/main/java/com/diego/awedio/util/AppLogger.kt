package com.diego.awedio.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogLevel,
    val message: String
) {
    fun formattedText(): String {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        val levelStr = when (level) {
            LogLevel.INFO -> "INFO"
            LogLevel.WARN -> "WARN"
            LogLevel.ERROR -> "ERROR"
        }
        return "[$timeStr] [$levelStr] [$tag]: $message"
    }

    internal fun toFileLine(): String {
        val escaped = message.replace("\\", "\\\\").replace("\n", "\\n")
        return "$timestamp|${level.name}|$tag|$escaped"
    }
}

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOGS = 200
    private const val MAX_FILE_BYTES = 512L * 1024
    private const val TRUNCATE_KEEP_BYTES = 256L * 1024

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val fileExecutor = Executors.newSingleThreadExecutor()
    private var logFile: File? = null

    @Volatile
    private var crashHandlerInstalled = false

    fun init(context: Context) {
        if (logFile != null) return
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "app.log")
            logFile = file
            loadFromFile(file)
            fileExecutor.execute { truncateIfNeeded(file) }
            i(TAG, "=== App session started (file logging enabled: ${file.absolutePath}) ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logging", e)
        }
    }

    fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeNow(
                    LogEntry(
                        tag = "Crash",
                        level = LogLevel.ERROR,
                        message = "Uncaught exception on thread \"${thread.name}\": ${Log.getStackTraceString(throwable)}"
                    )
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun getLogFile(): File? = logFile

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addEntry(LogEntry(tag = tag, level = LogLevel.INFO, message = message))
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addEntry(LogEntry(tag = tag, level = LogLevel.WARN, message = message))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message\nException: ${throwable.message}" else message
        Log.e(tag, fullMsg, throwable)
        addEntry(LogEntry(tag = tag, level = LogLevel.ERROR, message = fullMsg))
    }

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
        }
        val file = logFile ?: return
        fileExecutor.execute {
            try {
                file.writeText("")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log file", e)
            }
        }
    }

    fun getAllLogsText(): String {
        return _logs.value.joinToString("\n") { it.formattedText() }
    }

    private fun addEntry(entry: LogEntry) {
        synchronized(this) {
            val current = _logs.value.toMutableList()
            current.add(entry)
            if (current.size > MAX_LOGS) {
                current.removeAt(0)
            }
            _logs.value = current
        }
        writeAsync(entry)
    }

    private fun writeAsync(entry: LogEntry) {
        val file = logFile ?: return
        fileExecutor.execute {
            try {
                appendToFile(file, entry)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log entry to file", e)
            }
        }
    }

    private fun writeNow(entry: LogEntry) {
        val file = logFile ?: return
        appendToFile(file, entry)
    }

    private fun appendToFile(file: File, entry: LogEntry) {
        truncateIfNeeded(file)
        file.appendText(entry.toFileLine() + "\n")
    }

    private fun truncateIfNeeded(file: File) {
        try {
            if (file.length() <= MAX_FILE_BYTES) return
            val bytes = file.readBytes()
            val keepFrom = (bytes.size - TRUNCATE_KEEP_BYTES.toInt()).coerceAtLeast(0)
            var start = keepFrom
            for (i in keepFrom until bytes.size) {
                if (bytes[i] == '\n'.code.toByte()) {
                    start = i + 1
                    break
                }
            }
            file.writeBytes(bytes.copyOfRange(start, bytes.size))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to truncate log file", e)
        }
    }

    private fun loadFromFile(file: File) {
        if (!file.exists()) return
        val entries = mutableListOf<LogEntry>()
        try {
            file.useLines { lines ->
                for (line in lines) {
                    parseFileLine(line)?.let { entries.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read existing log file", e)
        }
        _logs.value = if (entries.size > MAX_LOGS) entries.takeLast(MAX_LOGS) else entries
    }

    private fun parseFileLine(line: String): LogEntry? {
        if (line.isBlank()) return null
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val level = try {
            LogLevel.valueOf(parts[1])
        } catch (_: IllegalArgumentException) {
            LogLevel.INFO
        }
        return LogEntry(
            timestamp = timestamp,
            tag = parts[2],
            level = level,
            message = unescape(parts[3])
        )
    }

    private fun unescape(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n' -> {
                        sb.append('\n')
                        i += 2
                        continue
                    }
                    '\\' -> {
                        sb.append('\\')
                        i += 2
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
