package com.antlers.support.highlighting

import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.antlers.support.lexer.AntlersTokenTypes

class AntlersEditorHighlighter(
    project: Project?,
    virtualFile: VirtualFile?,
    colors: EditorColorsScheme
) : LayeredLexerEditorHighlighter(AntlersSyntaxHighlighter(), colors) {
    init {
        val templateLang = if (project != null && virtualFile != null) {
            TemplateDataLanguageMappings.getInstance(project).getMapping(virtualFile)
        } else {
            null
        } ?: HTMLLanguage.INSTANCE

        val innerHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
            templateLang, project, virtualFile
        )
        registerLayer(AntlersTokenTypes.TEMPLATE_TEXT, LayerDescriptor(innerHighlighter, ""))
    }
}
