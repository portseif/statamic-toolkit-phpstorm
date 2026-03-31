package com.antlers.support.formatting

import com.intellij.formatting.*
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.antlers.support.psi.AntlersTypes
import com.antlers.support.lexer.AntlersTokenTypes

class AntlersBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    blockFactory: TemplateLanguageBlockFactory,
    codeStyleSettings: CodeStyleSettings,
    foreignChildren: List<DataLanguageBlockWrapper>?
) : TemplateLanguageBlock(node, wrap, alignment, blockFactory, codeStyleSettings, foreignChildren) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun getIndent(): Indent {
        return when (myNode.elementType) {
            // TAG_EXPRESSION and CLOSING_TAG are children of ANTLERS_TAG; giving them
            // a normal indent keeps tag content aligned inside {{ }}.
            // CONDITIONAL_TAG must use getNoneIndent() so that {{ else }}, {{ elseif }},
            // {{ endif }}, and {{ endunless }} are NOT indented inside the delimiters
            // — they should sit flush with {{ if }} / {{ unless }}, not nested inside them.
            AntlersTypes.TAG_EXPRESSION,
            AntlersTypes.CLOSING_TAG -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    override fun getTemplateTextElementType() = AntlersTokenTypes.TEMPLATE_TEXT
}
