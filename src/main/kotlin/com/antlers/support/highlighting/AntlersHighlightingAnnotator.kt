package com.antlers.support.highlighting

import com.antlers.support.AntlersBlockTags
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
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

class AntlersHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!AntlersSettings.getInstance().state.enableSemanticHighlighting) return

        val elementType = element.node?.elementType ?: return

        // Highlight identifiers inside tag names, modifiers, and parameters
        if (elementType == AntlersTokenTypes.IDENTIFIER) {
            when (val parent = element.parent) {
                is AntlersTagName -> highlightTagNamePart(element, parent, holder)
                is AntlersModifier -> {
                    if (parent.identifier == element) {
                        applyHighlight(holder, element, AntlersHighlighterColors.MODIFIER)
                    }
                }
                is AntlersParameter -> {
                    if (element.textRange.endOffset <= parent.opAssign.textRange.startOffset) {
                        applyHighlight(holder, element, AntlersHighlighterColors.PARAMETER)
                    }
                }
            }
        }

        // Highlight separators inside tag names.
        // The `/` in partial paths (e.g. partial:components/hero) keeps the TAG_PATH
        // attribute so it stays navigable/underlined. The `:` delimiter in tag names
        // (e.g. collection:count, form:create) uses the generic DELIMITER attribute
        // to visually distinguish it from the identifier parts on either side.
        if (elementType == AntlersTokenTypes.COLON || elementType == AntlersTokenTypes.OP_DIVIDE) {
            val parent = element.parent
            if (parent is AntlersTagName && isTagLike(parent)) {
                val fullText = parent.text ?: return
                val attributes = if (elementType == AntlersTokenTypes.OP_DIVIDE && isPartialPath(fullText)) {
                    AntlersHighlighterColors.TAG_PATH
                } else {
                    AntlersHighlighterColors.DELIMITER
                }
                applyHighlight(holder, element, attributes)
            }
        }
    }

    private fun highlightTagNamePart(element: PsiElement, tagName: AntlersTagName, holder: AnnotationHolder) {
        // Only highlight tag names that look like actual tags (namespaced, have params, or closing)
        // This avoids coloring simple variables like {{ url }} or {{ title }}
        if (!isTagLike(tagName)) return

        val fullText = tagName.text ?: return
        val isHead = element.prevSibling == null

        if (isPartialPath(fullText)) {
            // Head gets tag color, path parts get underlined tag color
            if (isHead) {
                applyHighlight(holder, element, AntlersHighlighterColors.TAG_NAME)
            } else {
                applyHighlight(holder, element, AntlersHighlighterColors.TAG_PATH)
            }
        } else {
            // All parts of namespaced/parameterized tag names get the tag color
            applyHighlight(holder, element, AntlersHighlighterColors.TAG_NAME)
        }
    }

    /**
     * Determines if a tag name should be highlighted as a tag (not a variable).
     * Tags are distinguished by being: closing tags, having parameters, containing
     * a namespace (:), or containing a path separator (/).
     */
    private fun isTagLike(tagName: AntlersTagName): Boolean {
        return when (val container = tagName.parent) {
            is AntlersClosingTag -> true
            is AntlersTagExpression -> {
                container.parameterList.isNotEmpty() ||
                    AntlersBlockTags.isBlockTag(tagName.text) ||
                    tagName.text.contains(':') ||
                    tagName.text.contains('/')
            }
            else -> false
        }
    }

    private fun isPartialPath(tagNameText: String): Boolean {
        return tagNameText.startsWith("partial:") ||
            tagNameText.startsWith("partial/")
    }

    private fun applyHighlight(
        holder: AnnotationHolder,
        element: PsiElement,
        attributesKey: TextAttributesKey
    ) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(attributesKey)
            .create()
    }
}
