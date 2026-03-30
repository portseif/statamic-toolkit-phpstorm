package com.antlers.support.actions

import com.antlers.support.AntlersIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import java.io.IOException

abstract class CreateStatamicControllerAction(
    text: String,
    description: String,
    private val templateBuilder: (String) -> String
) : DumbAwareAction(text, description, AntlersIcons.FILE) {
    private val dialogTitle = text

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = actionContext(event).hasLaravelStructure
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val basePath = actionContext(event).basePath ?: return
        val rawName = Messages.showInputDialog(
            project,
            "Controller class name:",
            dialogTitle,
            Messages.getQuestionIcon()
        ) ?: return

        val className = StatamicSnippetTemplates.normalizeControllerClassName(rawName)
        if (className == null) {
            Messages.showErrorDialog(project, "Enter a valid controller class name.", dialogTitle)
            return
        }

        try {
            WriteCommandAction.runWriteCommandAction(project, dialogTitle, null, Runnable {
                val controllerDir = VfsUtil.createDirectoryIfMissing(basePath.resolve("app/Http/Controllers").toString())
                    ?: error("Unable to create app/Http/Controllers")
                val fileName = "$className.php"
                val existing = controllerDir.findChild(fileName)
                if (existing != null) {
                    FileEditorManager.getInstance(project).openFile(existing, true)
                    return@Runnable
                }
                val file = controllerDir.createChildData(this, fileName)
                VfsUtil.saveText(file, templateBuilder(className))
                FileEditorManager.getInstance(project).openFile(file, true)
            })
        } catch (exception: IOException) {
            Messages.showErrorDialog(project, exception.message ?: "Failed to create controller.", dialogTitle)
        } catch (exception: IllegalStateException) {
            Messages.showErrorDialog(project, exception.message ?: "Failed to create controller.", dialogTitle)
        }
    }
}

class CreateBasicStatamicControllerAction : CreateStatamicControllerAction(
    text = "Create Basic Statamic Controller...",
    description = "Create a Laravel-style controller that renders a view.",
    templateBuilder = StatamicSnippetTemplates::buildBasicController
)

class CreateAntlersViewControllerAction : CreateStatamicControllerAction(
    text = "Create Antlers View Controller...",
    description = "Create a controller that returns a Statamic View with template and layout.",
    templateBuilder = StatamicSnippetTemplates::buildAntlersViewController
)
