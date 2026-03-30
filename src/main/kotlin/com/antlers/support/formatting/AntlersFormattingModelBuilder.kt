package com.antlers.support.formatting

import com.intellij.formatting.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.templateLanguages.SimpleTemplateLanguageFormattingModelBuilder
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageFormattingModelBuilder
import com.intellij.psi.tree.OuterLanguageElementType

class AntlersFormattingModelBuilder : TemplateLanguageFormattingModelBuilder() {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.containingFile
        val node = formattingContext.node

        if (node.elementType is OuterLanguageElementType) {
            return SimpleTemplateLanguageFormattingModelBuilder().createModel(formattingContext)
        }

        val settings = formattingContext.codeStyleSettings
        val rootBlock = getRootBlock(file, file.viewProvider, settings)
        return DocumentBasedFormattingModel(rootBlock, settings, file)
    }

    override fun createTemplateLanguageBlock(
        node: com.intellij.lang.ASTNode,
        wrap: Wrap?,
        alignment: Alignment?,
        foreignChildren: MutableList<DataLanguageBlockWrapper>?,
        settings: CodeStyleSettings
    ): TemplateLanguageBlock {
        return AntlersBlock(node, wrap, alignment, this, settings, foreignChildren)
    }
}
