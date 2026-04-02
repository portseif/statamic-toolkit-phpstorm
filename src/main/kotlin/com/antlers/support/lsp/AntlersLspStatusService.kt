package com.antlers.support.lsp

import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.CopyOnWriteArrayList

enum class AntlersLspConnectionState {
    WAITING,
    STARTING,
    CONNECTED,
    ERROR
}

data class AntlersLspStatusSnapshot(
    val state: AntlersLspConnectionState,
    val message: String
)

class AntlersLspStatusService {
    @Volatile private var snapshot = AntlersLspStatusSnapshot(
        AntlersLspConnectionState.WAITING,
        "Open an Antlers file to start the language server"
    )
    private val listeners = CopyOnWriteArrayList<Runnable>()

    fun markWaiting(message: String = "Open an Antlers file to start the language server") {
        updateSnapshot(AntlersLspStatusSnapshot(AntlersLspConnectionState.WAITING, message))
    }

    fun markStarting(message: String = "Starting Antlers language server…") {
        updateSnapshot(AntlersLspStatusSnapshot(AntlersLspConnectionState.STARTING, message))
    }

    fun markConnected(message: String = "Connected") {
        updateSnapshot(AntlersLspStatusSnapshot(AntlersLspConnectionState.CONNECTED, message))
    }

    fun markError(message: String = "Connection failed") {
        updateSnapshot(AntlersLspStatusSnapshot(AntlersLspConnectionState.ERROR, message))
    }

    fun snapshot(): AntlersLspStatusSnapshot = snapshot

    fun addChangeListener(listener: Runnable) {
        listeners += listener
    }

    fun removeChangeListener(listener: Runnable) {
        listeners -= listener
    }

    private fun updateSnapshot(next: AntlersLspStatusSnapshot) {
        if (snapshot == next) return
        snapshot = next
        notifyListeners()
    }

    private fun notifyListeners() {
        if (listeners.isEmpty()) return

        val application = ApplicationManager.getApplication()
        if (application == null || application.isUnitTestMode) {
            listeners.forEach(Runnable::run)
            return
        }

        application.invokeLater {
            listeners.forEach(Runnable::run)
        }
    }
}
