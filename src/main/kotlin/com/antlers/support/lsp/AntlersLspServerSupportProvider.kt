package com.antlers.support.lsp

import com.antlers.support.AntlersIcons
import com.antlers.support.settings.AntlersSettingsConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

class AntlersLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter
    ) {
        if (!file.name.contains(".antlers.")) return

        val statusService = project.getService(AntlersLspStatusService::class.java)
        statusService?.markStarting()

        try {
            serverStarter.ensureServerStarted(AntlersLspServerDescriptor(project))
        } catch (t: Throwable) {
            statusService?.markError(t.message ?: "Failed to start language server")
            throw t
        }
    }

    override fun createLspServerWidgetItem(
        lspServer: LspServer,
        currentFile: VirtualFile?
    ): LspServerWidgetItem {
        syncStatus(lspServer)
        return AntlersTrackedLspServerWidgetItem(lspServer, currentFile)
    }

    private fun syncStatus(lspServer: LspServer) {
        val statusService = lspServer.project.getService(AntlersLspStatusService::class.java) ?: return
        when (lspServer.state) {
            LspServerState.Initializing -> statusService.markStarting("Initializing Antlers language server…")
            LspServerState.Running -> statusService.markConnected("Connected")
            LspServerState.ShutdownNormally -> statusService.markWaiting("Idle")
            LspServerState.ShutdownUnexpectedly -> statusService.markError("Disconnected unexpectedly")
        }
    }
}

private class AntlersTrackedLspServerWidgetItem(
    lspServer: LspServer,
    currentFile: VirtualFile?
) : LspServerWidgetItem(
    lspServer,
    currentFile,
    AntlersIcons.FILE,
    AntlersSettingsConfigurable::class.java
)
