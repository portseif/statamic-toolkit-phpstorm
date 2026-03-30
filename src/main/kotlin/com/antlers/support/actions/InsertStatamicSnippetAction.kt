package com.antlers.support.actions

import com.antlers.support.AntlersIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.DumbAwareAction

abstract class InsertStatamicSnippetAction(
    text: String,
    description: String,
    private val template: StatamicInsertTemplate
) : DumbAwareAction(text, description, AntlersIcons.FILE) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val context = actionContext(event)
        val hasEditor = event.getData(CommonDataKeys.EDITOR) != null
        event.presentation.isEnabledAndVisible = context.isPhpFile && hasEditor
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        WriteCommandAction.runWriteCommandAction(project, template.title, null, Runnable {
            EditorModificationUtil.insertStringAtCaret(editor, template.content, true, true)
        })
    }
}

class InsertEntryQueryAction : InsertStatamicSnippetAction(
    text = "Insert Entry Query",
    description = "Insert a Statamic entry query snippet.",
    template = StatamicSnippetTemplates.entryQuery
)

class InsertSingleEntryQueryAction : InsertStatamicSnippetAction(
    text = "Insert Single Entry Query",
    description = "Insert a Statamic query that returns one entry.",
    template = StatamicSnippetTemplates.singleEntryQuery
)

class InsertPaginatedEntriesQueryAction : InsertStatamicSnippetAction(
    text = "Insert Paginated Entry Query",
    description = "Insert a paginated Statamic entry query snippet.",
    template = StatamicSnippetTemplates.paginatedEntriesQuery
)

class InsertGlobalSetQueryAction : InsertStatamicSnippetAction(
    text = "Insert Global Set Lookup",
    description = "Insert a Statamic global set lookup snippet.",
    template = StatamicSnippetTemplates.globalSetQuery
)
