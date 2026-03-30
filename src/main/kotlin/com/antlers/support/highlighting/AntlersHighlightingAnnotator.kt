package com.antlers.support.highlighting

import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.psi.AntlersClosingTag
import com.antlers.support.psi.AntlersModifier
import com.antlers.support.psi.AntlersParameter
import com.antlers.support.psi.AntlersTagExpression
import com.antlers.support.psi.AntlersTagName
import com.antlers.support.settings.AntlersSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

class AntlersHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!AntlersSettings.getInstance().state.enableSemanticHighlighting) return
        when (val parent = element.parent) {
            is AntlersTagName -> {
                if (shouldHighlightTagHead(element, parent)) {
                    applyHighlight(holder, element, AntlersHighlighterColors.TAG_NAME)
                }
            }
            is AntlersModifier -> {
                if (parent.identifier == element) {
                    applyHighlight(holder, element, AntlersHighlighterColors.TAG_NAME)
                }
            }

            is AntlersParameter -> {
                if (element.textRange.endOffset <= parent.opAssign.textRange.startOffset) {
                    applyHighlight(holder, element, AntlersHighlighterColors.PARAMETER)
                }
            }
        }
    }

    private fun applyHighlight(
        holder: AnnotationHolder,
        element: PsiElement,
        attributesKey: com.intellij.openapi.editor.colors.TextAttributesKey
    ) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(attributesKey)
            .create()
    }

    private fun shouldHighlightTagHead(element: PsiElement, tagName: AntlersTagName): Boolean {
        if (element.node?.elementType != AntlersTokenTypes.IDENTIFIER) return false
        if (element.prevSibling != null) return false

        return when (val container = tagName.parent) {
            is AntlersClosingTag -> true
            is AntlersTagExpression -> {
                container.parameterList.isNotEmpty() ||
                    tagName.text.contains(':') ||
                    tagName.text.contains('/')
            }

            else -> false
        }
    }
}
