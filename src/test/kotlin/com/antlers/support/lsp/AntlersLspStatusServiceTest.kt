package com.antlers.support.lsp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AntlersLspStatusServiceTest {
    @Test
    fun startsInWaitingState() {
        val service = AntlersLspStatusService()

        assertEquals(
            AntlersLspStatusSnapshot(
                AntlersLspConnectionState.WAITING,
                "Open an Antlers file to start the language server"
            ),
            service.snapshot()
        )
    }

    @Test
    fun updatesConnectionStates() {
        val service = AntlersLspStatusService()

        service.markStarting("Launching…")
        assertEquals(
            AntlersLspStatusSnapshot(AntlersLspConnectionState.STARTING, "Launching…"),
            service.snapshot()
        )

        service.markConnected()
        assertEquals(
            AntlersLspStatusSnapshot(AntlersLspConnectionState.CONNECTED, "Connected"),
            service.snapshot()
        )

        service.markError("Disconnected unexpectedly")
        assertEquals(
            AntlersLspStatusSnapshot(AntlersLspConnectionState.ERROR, "Disconnected unexpectedly"),
            service.snapshot()
        )
    }

    @Test
    fun onlyNotifiesListenersWhenStateActuallyChanges() {
        val service = AntlersLspStatusService()
        val notifications = AtomicInteger()
        val listener = Runnable { notifications.incrementAndGet() }

        service.addChangeListener(listener)
        service.markWaiting()
        service.markStarting()
        service.markStarting()
        service.markConnected()
        service.removeChangeListener(listener)
        service.markError()

        assertEquals(2, notifications.get())
    }
}
