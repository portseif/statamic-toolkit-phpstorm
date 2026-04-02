package com.antlers.support.lsp

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AntlersLspServerDescriptor(project: Project) :
    ProjectWideLspServerDescriptor(project, "Antlers Language Server") {

    override fun isSupportedFile(file: VirtualFile): Boolean {
        return file.name.contains(".antlers.")
    }

    override fun createCommandLine(): GeneralCommandLine {
        val statusService = project.getService(AntlersLspStatusService::class.java)
        statusService?.markStarting("Launching Antlers language server…")

        return try {
            val serverJs = extractBundledServer()
            val nodePath = findNodeJs()

            GeneralCommandLine(nodePath, serverJs.toString(), "--stdio").apply {
                withWorkDirectory(project.basePath)
            }
        } catch (t: Throwable) {
            statusService?.markError(t.message ?: "Failed to launch language server")
            throw t
        }
    }

    /**
     * Extracts the bundled antlersls.js from the plugin JAR to a temp directory.
     * The extracted copy is patched to avoid a VS Code-specific client request
     * that JetBrains' generic LSP client does not implement.
     */
    private fun extractBundledServer(): Path {
        val targetDir = Path.of(System.getProperty("java.io.tmpdir"), "antlers-lsp")
        val targetFile = targetDir.resolve("antlersls-statamic-toolkit.js")

        Files.createDirectories(targetDir)
        val resource = javaClass.getResourceAsStream("/language-server/antlersls.js")
            ?: error("Bundled Antlers language server not found in plugin resources")

        val preparedBytes = resource.use { input ->
            prepareBundledServerScript(String(input.readAllBytes(), StandardCharsets.UTF_8))
                .toByteArray(StandardCharsets.UTF_8)
        }

        val needsWrite = !Files.exists(targetFile) || !Files.readAllBytes(targetFile).contentEquals(preparedBytes)
        if (needsWrite) {
            Files.write(targetFile, preparedBytes)
        }

        return targetFile
    }

    /**
     * Finds the Node.js binary. Tries PhpStorm's configured interpreter first,
     * then falls back to 'node' on the system PATH.
     */
    private fun findNodeJs(): String {
        // Try PhpStorm's Node.js interpreter manager
        try {
            val interpreterClass = Class.forName("com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager")
            val getInstance = interpreterClass.getMethod("getInstance", Project::class.java)
            val manager = getInstance.invoke(null, project)
            val getInterpreterRef = manager.javaClass.getMethod("getInterpreterRef")
            val ref = getInterpreterRef.invoke(manager)
            val resolve = ref.javaClass.getMethod("resolve", Project::class.java)
            val interpreter = resolve.invoke(ref, project)
            if (interpreter != null) {
                val referenceName = interpreter.javaClass.getMethod("getReferenceName")
                val path = referenceName.invoke(interpreter) as? String
                if (path != null && File(path).exists()) return path
            }
        } catch (_: Exception) {
            // Node.js plugin not available or interpreter not configured
        }

        // Fall back to 'node' on PATH
        return "node"
    }

    override fun getLanguageId(file: VirtualFile): String = "antlers"

    override val lspCompletionSupport: LspCompletionSupport
        get() = DISABLED_COMPLETION_SUPPORT

    override val lspFormattingSupport: LspFormattingSupport
        get() = NATIVE_FORMATTING_SUPPORT

    // Disable LSP features that our native PSI already handles well
    override val lspHoverSupport: Boolean get() = false
    override val lspGoToDefinitionSupport: Boolean get() = false

    internal companion object {
        internal val DISABLED_COMPLETION_SUPPORT: LspCompletionSupport = object : LspCompletionSupport() {
            override fun shouldRunCodeCompletion(parameters: CompletionParameters): Boolean = false
        }

        internal val NATIVE_FORMATTING_SUPPORT: LspFormattingSupport = object : LspFormattingSupport() {
            override fun shouldFormatThisFileExclusivelyByServer(
                file: VirtualFile,
                hasFormatRanges: Boolean,
                isExplicitFormat: Boolean
            ): Boolean = false
        }

        private const val PROJECT_DETAILS_AVAILABLE_REQUEST =
            """;let e={content:t};Fe.sendRequest("antlers/projectDetailsAvailable",e)"""

        internal fun prepareBundledServerScript(script: String): String {
            return script.replace(PROJECT_DETAILS_AVAILABLE_REQUEST, "")
        }
    }
}
