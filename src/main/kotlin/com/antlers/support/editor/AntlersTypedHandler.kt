package com.antlers.support.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.antlers.support.settings.AntlersSettings

class AntlersTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '{') return Result.CONTINUE
        if (!file.name.contains(".antlers.")) return Result.CONTINUE
        if (!AntlersSettings.getInstance().state.enableAutoCloseDelimiters) return Result.CONTINUE

        val offset = editor.caretModel.offset
        val document = editor.document
        val chars = document.charsSequence

        if (offset < 2) return Result.CONTINUE

        // Check if we just completed {{
        if (chars[offset - 2] != '{') return Result.CONTINUE

        // Don't auto-close if }} already follows
        if (offset + 1 < chars.length && chars[offset] == '}' && chars[offset + 1] == '}') {
            return Result.CONTINUE
        }

        // Insert "  }}" with cursor between the spaces
        EditorModificationUtil.insertStringAtCaret(editor, "  }}", false, true, 1)
        return Result.STOP
    }
}
