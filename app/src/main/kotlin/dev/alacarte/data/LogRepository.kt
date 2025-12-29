package dev.alacarte.data

import androidx.compose.runtime.mutableStateListOf

object LogRepository {
    private val _logs = mutableStateListOf<String>()
    val logs: List<String> get() = _logs

    fun addLog(msg: String) {
        _logs.add(msg)
    }

    fun clearLogs() {
        _logs.clear()
    }
}
