package com.antlers.support.folding

import com.antlers.support.psi.*
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Provides code folding for Antlers templates:
 *
 *  - Tag pairs:  {{ collection:blog }}...{{ /collection:blog }}
 *  - Conditionals: {{ if ... }}...{{ endif }}  /  {{ unless }}...{{ endunless }}
 *  - Comments:   {{# long comment #}}
 *  - Noparse:    {{ noparse }}...{{ /noparse }}
 *
 * Opening and closing tags are matched with a stack so nested pairs fold
 * independently (e.g. a nested {{ if }} inside {{ collection }} both fold).
 *
 * Implements [DumbAware] so folding works during initial indexing.
 */
class AntlersFoldingBuilder : FoldingBuilderEx(), DumbAware {

    /** Tracks an unmatched opening tag on the fold stack. */
    private data class OpenFold(val tag: AntlersAntlersTag, val key: String)

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        // Stack maps a synthetic key (tag name or "COND_IF" / "COND_UNLESS") to
        // its unmatched opening AntlersAntlersTag, in document order.
        val openStack = ArrayDeque<OpenFold>()

        for (child in root.children) {
            when (child) {
                is AntlersAntlersTag  -> processTag(child, openStack, descriptors, document)
                is AntlersComment     -> foldIfMultiline(child, descriptors, document)
                is AntlersNoparseBlock -> foldIfMultiline(child, descriptors, document)
            }
        }

        return descriptors.toTypedArray()
    }

    // -------------------------------------------------------------------------
    // Tag processing
    // -------------------------------------------------------------------------

    private fun processTag(
        tag: AntlersAntlersTag,
        openStack: ArrayDeque<OpenFold>,
        descriptors: MutableList<FoldingDescriptor>,
        document: Document
    ) {
        val tagExpr   = tag.tagExpression
        val closingTag = tag.closingTag
        val condTag   = tag.conditionalTag
        val isSelfClose = tag.tagSelfClose != null

        when {
            // Opening regular tag: {{ collection:blog }}  (not self-closing /}})
            tagExpr != null && !isSelfClose -> {
                openStack.addLast(OpenFold(tag, tagExpr.tagName.text))
            }

            // Closing regular tag: {{ /collection:blog }}
            // Also handles {{ /if }} and {{ /unless }}: after the grammar fix these
            // parse as closingTag (tagNameAtom now accepts KEYWORD_IF/UNLESS), so map
            // their names to the conditional stack keys used by processConditional().
            closingTag != null -> {
                val name = closingTag.tagName?.text ?: return
                val stackKey = when (name) {
                    "if"     -> "COND_IF"
                    "unless" -> "COND_UNLESS"
                    else     -> name
                }
                val matchIdx = openStack.indexOfLast { it.key == stackKey }
                if (matchIdx >= 0) {
                    addPairFold(openStack.removeAt(matchIdx).tag, tag, descriptors, document)
                }
            }

            // Conditional block tags: if / unless / endif / endunless
            condTag != null -> processConditional(condTag, tag, openStack, descriptors, document)
        }
    }

    private fun processConditional(
        cond: AntlersConditionalTag,
        tag: AntlersAntlersTag,
        openStack: ArrayDeque<OpenFold>,
        descriptors: MutableList<FoldingDescriptor>,
        document: Document
    ) {
        when {
            cond.keywordIf     != null -> openStack.addLast(OpenFold(tag, "COND_IF"))
            cond.keywordUnless != null -> openStack.addLast(OpenFold(tag, "COND_UNLESS"))
            cond.keywordEndif  != null -> {
                val matchIdx = openStack.indexOfLast { it.key == "COND_IF" }
                if (matchIdx >= 0) {
                    addPairFold(openStack.removeAt(matchIdx).tag, tag, descriptors, document)
                }
            }
            cond.keywordEndunless != null -> {
                val matchIdx = openStack.indexOfLast { it.key == "COND_UNLESS" }
                if (matchIdx >= 0) {
                    addPairFold(openStack.removeAt(matchIdx).tag, tag, descriptors, document)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Adds a folding region spanning [openTag, closeTag] if they are on different lines. */
    private fun addPairFold(
        openTag: AntlersAntlersTag,
        closeTag: AntlersAntlersTag,
        descriptors: MutableList<FoldingDescriptor>,
        document: Document
    ) {
        val start = openTag.textRange.startOffset
        val end   = closeTag.textRange.endOffset
        if (document.getLineNumber(start) < document.getLineNumber(end)) {
            descriptors.add(FoldingDescriptor(openTag.node, TextRange(start, end)))
        }
    }

    /** Folds a standalone block element (comment, noparse) if it spans multiple lines. */
    private fun foldIfMultiline(
        element: PsiElement,
        descriptors: MutableList<FoldingDescriptor>,
        document: Document
    ) {
        val range = element.textRange
        if (document.getLineNumber(range.startOffset) < document.getLineNumber(range.endOffset)) {
            descriptors.add(FoldingDescriptor(element.node, range))
        }
    }

    // -------------------------------------------------------------------------
    // Placeholder text (shown while folded)
    // -------------------------------------------------------------------------

    override fun getPlaceholderText(node: ASTNode): String = when (val psi = node.psi) {
        is AntlersAntlersTag -> {
            val condTag = psi.conditionalTag
            when {
                psi.tagExpression != null       -> "{{ ${psi.tagExpression!!.tagName.text} }}..."
                condTag?.keywordIf     != null  -> "{{ if }}..."
                condTag?.keywordUnless != null  -> "{{ unless }}..."
                else                            -> "{{ ... }}"
            }
        }
        is AntlersComment      -> "{{# ... #}}"
        is AntlersNoparseBlock -> "{{ noparse }}..."
        else                   -> "..."
    }

    /** Tag pairs and block constructs start expanded — let the user collapse them. */
    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
