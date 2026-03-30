package com.antlers.support.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

class AntlersEnterHandler : EnterHandlerDelegateAdapter() {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (!file.name.contains(".antlers.")) return EnterHandlerDelegate.Result.Continue

        val offset = caretOffset.get()
        val document = editor.document
        val chars = document.charsSequence

        if (offset < 2 || offset + 2 >= chars.length) return EnterHandlerDelegate.Result.Continue

        // Check if caret is between {{ tag }}|{{ /tag }}
        // Look backwards for }}
        if (!findTokenBefore(chars, offset, "}}")) return EnterHandlerDelegate.Result.Continue

        // Look forwards for {{ /
        val afterOpenIdx = indexOfSkippingWhitespace(chars, offset, "{{")
        if (afterOpenIdx < 0) return EnterHandlerDelegate.Result.Continue

        // Check that {{ is followed by optional whitespace then /
        var checkIdx = afterOpenIdx + 2
        while (checkIdx < chars.length && chars[checkIdx].isWhitespace()) checkIdx++
        if (checkIdx >= chars.length || chars[checkIdx] != '/') return EnterHandlerDelegate.Result.Continue

        // Insert extra newline for the closing tag
        caretAdvance.set(1)
        return EnterHandlerDelegate.Result.Default
    }

    private fun findTokenBefore(chars: CharSequence, offset: Int, token: String): Boolean {
        var idx = offset - 1
        while (idx >= 0 && chars[idx].isWhitespace()) idx--
        if (idx < token.length - 1) return false
        val start = idx - token.length + 1
        return chars.subSequence(start, idx + 1).toString() == token
    }

    private fun indexOfSkippingWhitespace(chars: CharSequence, offset: Int, token: String): Int {
        var idx = offset
        while (idx < chars.length && chars[idx].isWhitespace()) idx++
        if (idx + token.length > chars.length) return -1
        return if (chars.subSequence(idx, idx + token.length).toString() == token) idx else -1
    }
}
