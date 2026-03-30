package com.antlers.support.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal data class StatamicActionContext(
    val basePath: Path?,
    val fileExtension: String?
) {
    val isPhpFile: Boolean
        get() = fileExtension.equals("php", ignoreCase = true)

    val hasLaravelStructure: Boolean
        get() = basePath?.let { base ->
            Files.isRegularFile(base.resolve("artisan")) ||
                Files.isDirectory(base.resolve("app/Http/Controllers")) ||
                Files.isRegularFile(base.resolve("routes/web.php"))
        } == true
}

internal fun actionContext(event: AnActionEvent): StatamicActionContext {
    val projectPath = event.project?.basePath?.let(Paths::get)
    val extension = event.getData(CommonDataKeys.VIRTUAL_FILE)?.extension
        ?: event.getData(CommonDataKeys.PSI_FILE)?.virtualFile?.extension
    return StatamicActionContext(projectPath, extension)
}
