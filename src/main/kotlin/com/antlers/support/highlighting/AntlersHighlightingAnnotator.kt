package com.antlers.support.highlighting

import com.antlers.support.AntlersBlockTags
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.psi.AntlersClosingTag
import com.antlers.support.psi.AntlersModifier
import com.antlers.support.psi.AntlersParameter
import com.antlers.support.psi.AntlersTagExpression
import com.antlers.support.psi.AntlersTagName
import com.antlers.support.settings.AntlersSettings
import com.antlers.support.statamic.StatamicCatalog
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

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
        // attribute so the path segment can be styled separately from the tag head.
        // The `:` delimiter in tag names (e.g. collection:count, form:create) uses
        // the generic DELIMITER attribute to distinguish it from the identifier parts
        // on either side.
        if (elementType == AntlersTokenTypes.COLON || elementType == AntlersTokenTypes.OP_DIVIDE) {
            highlightDelimiter(element, elementType, holder)
        }
    }

    private fun highlightTagNamePart(element: PsiElement, tagName: AntlersTagName, holder: AnnotationHolder) {
        // Only highlight tag names that look like actual tags, not namespaced variables.
        // This avoids coloring simple variables like {{ url }} or {{ title }}
        if (!isTagLike(tagName)) return

        val fullText = tagName.text ?: return
        val isHead = element.prevSibling == null

        if (isPartialTag(fullText)) {
            // Keep the tag head on TAG_NAME and style only the actual partial path
            // after partial: / partial/.
            if (isHead) {
                applyHighlight(holder, element, AntlersHighlighterColors.TAG_NAME)
            } else {
                applyHighlight(holder, element, AntlersHighlighterColors.TAG_PATH)
            }
        } else {
            applyHighlight(holder, element, AntlersHighlighterColors.TAG_NAME)
        }
    }

    private fun highlightDelimiter(
        element: PsiElement,
        elementType: IElementType,
        holder: AnnotationHolder
    ) {
        when (val parent = element.parent) {
            is AntlersTagName -> {
                val fullText = parent.text ?: return
                if (isTagLike(parent)) {
                    val attributes = if (elementType == AntlersTokenTypes.OP_DIVIDE && isPartialTag(fullText)) {
                        AntlersHighlighterColors.TAG_PATH
                    } else {
                        AntlersHighlighterColors.DELIMITER
                    }
                    applyHighlight(holder, element, attributes)
                    return
                }

                if (elementType == AntlersTokenTypes.COLON && isNamespacedVariableReference(fullText)) {
                    applyHighlight(holder, element, AntlersHighlighterColors.DELIMITER)
                }
            }

            is AntlersTagExpression -> {
                if (elementType == AntlersTokenTypes.COLON && isExpressionReferenceDelimiter(element)) {
                    applyHighlight(holder, element, AntlersHighlighterColors.DELIMITER)
                }
            }
        }
    }

    /**
     * Determines if a tag name should be highlighted as a tag (not a variable).
     * Tags are distinguished by structural context plus known Statamic roots. This
     * avoids coloring namespaced variable references like global:foo:bar as tags
     * when they are used as operands inside a larger expression.
     */
    private fun isTagLike(tagName: AntlersTagName): Boolean {
        return when (val container = tagName.parent) {
            is AntlersClosingTag -> true
            is AntlersTagExpression -> {
                val fullText = tagName.text
                if (container.parameterList.isNotEmpty()) return true
                if (AntlersBlockTags.isBlockTag(fullText)) return true
                if (isPartialTag(fullText)) return true
                if (StatamicCatalog.resolveTag(fullText) != null) return true
                if (looksLikeVariableReference(fullText, container)) return false
                fullText.contains(':') || fullText.contains('/')
            }
            else -> false
        }
    }

    private fun looksLikeVariableReference(tagNameText: String, expression: AntlersTagExpression): Boolean {
        val rootName = tagNameText.substringBefore(':').substringBefore('/')
        val hasTagRoot = StatamicCatalog.findTag(rootName) != null || AntlersBlockTags.isBlockTag(rootName)
        if (hasTagRoot) return false

        if (StatamicCatalog.findVariable(rootName) != null) return true
        if (tagNameText.count { it == ':' } > 1) return true

        return expression.children.any { child ->
            child !is AntlersTagName &&
                child !is AntlersModifier &&
                child !is AntlersParameter &&
                child.text.isNotBlank()
        }
    }

    private fun isNamespacedVariableReference(text: String): Boolean {
        return text.contains(':') || text.contains('/')
    }

    private fun isExpressionReferenceDelimiter(element: PsiElement): Boolean {
        val prev = previousNonWhitespaceSibling(element) ?: return false
        val next = nextNonWhitespaceSibling(element) ?: return false
        if (!isReferenceSegment(prev) || !isReferenceSegment(next)) return false

        val chainStart = findReferenceChainStart(prev)
        val beforeChain = previousNonWhitespaceSibling(chainStart)
        return beforeChain?.node?.elementType != AntlersTokenTypes.OP_TERNARY_QUESTION
    }

    private fun findReferenceChainStart(element: PsiElement): PsiElement {
        var current = element
        while (true) {
            val prev = previousNonWhitespaceSibling(current) ?: return current
            if (!isReferenceChainToken(prev)) return current
            current = prev
        }
    }

    private fun isReferenceSegment(element: PsiElement): Boolean {
        return element.node?.elementType == AntlersTokenTypes.IDENTIFIER
    }

    private fun isReferenceChainToken(element: PsiElement): Boolean {
        return isReferenceSegment(element) || element.node?.elementType == AntlersTokenTypes.COLON
    }

    private fun previousNonWhitespaceSibling(element: PsiElement): PsiElement? {
        var sibling = element.prevSibling
        while (sibling != null && sibling.text.isBlank()) {
            sibling = sibling.prevSibling
        }
        return sibling
    }

    private fun nextNonWhitespaceSibling(element: PsiElement): PsiElement? {
        var sibling = element.nextSibling
        while (sibling != null && sibling.text.isBlank()) {
            sibling = sibling.nextSibling
        }
        return sibling
    }

    private fun isPartialTag(tagNameText: String): Boolean {
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
