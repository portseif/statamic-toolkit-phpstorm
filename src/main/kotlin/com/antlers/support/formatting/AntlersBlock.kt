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
            AntlersTypes.TAG_EXPRESSION,
            AntlersTypes.CONDITIONAL_TAG,
            AntlersTypes.CLOSING_TAG -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    override fun getTemplateTextElementType() = AntlersTokenTypes.TEMPLATE_TEXT
}
