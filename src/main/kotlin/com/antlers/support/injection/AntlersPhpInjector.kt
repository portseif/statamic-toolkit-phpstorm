package com.antlers.support.injection

import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.psi.AntlersPhpEchoBlock
import com.antlers.support.psi.AntlersPhpRawBlock
import com.antlers.support.settings.AntlersSettings
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

class AntlersPhpInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (!AntlersSettings.getInstance().state.enablePhpInjection) return

        val phpLanguage = Language.findLanguageByID("PHP") ?: return

        when (context) {
            is AntlersPhpRawBlock -> {
                val range = contentRange(context, AntlersTokenTypes.PHP_RAW_CONTENT) ?: return
                registrar
                    .startInjecting(phpLanguage)
                    .addPlace("<?php ", " ?>", context as PsiLanguageInjectionHost, range)
                    .doneInjecting()
            }
            is AntlersPhpEchoBlock -> {
                val range = contentRange(context, AntlersTokenTypes.PHP_ECHO_CONTENT) ?: return
                registrar
                    .startInjecting(phpLanguage)
                    .addPlace("<?php echo ", "; ?>", context as PsiLanguageInjectionHost, range)
                    .doneInjecting()
            }
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(AntlersPhpRawBlock::class.java, AntlersPhpEchoBlock::class.java)
    }

    private fun contentRange(block: PsiElement, contentType: com.intellij.psi.tree.IElementType): TextRange? {
        val blockStart = block.textRange.startOffset
        var first: PsiElement? = null
        var last: PsiElement? = null

        var child = block.firstChild
        while (child != null) {
            if (child.node.elementType == contentType) {
                if (first == null) first = child
                last = child
            }
            child = child.nextSibling
        }

        if (first == null || last == null) return null

        val start = first.textRange.startOffset - blockStart
        val end = last.textRange.endOffset - blockStart
        return TextRange(start, end)
    }
}
