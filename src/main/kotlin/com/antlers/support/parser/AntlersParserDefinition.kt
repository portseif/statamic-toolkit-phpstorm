package com.antlers.support.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.antlers.support.AntlersLanguage
import com.antlers.support.file.AntlersFile
import com.antlers.support.lexer.AntlersLexerAdapter
import com.antlers.support.lexer.AntlersTokenSets

class AntlersParserDefinition : ParserDefinition {
    companion object {
        @JvmField
        val FILE = IFileElementType(AntlersLanguage.INSTANCE)
    }

    override fun createLexer(project: Project): Lexer = AntlersLexerAdapter()

    override fun createParser(project: Project): PsiParser = AntlersParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = AntlersTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = AntlersTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement {
        throw UnsupportedOperationException("Not yet implemented: ${node.elementType}")
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = AntlersFile(viewProvider)
}
