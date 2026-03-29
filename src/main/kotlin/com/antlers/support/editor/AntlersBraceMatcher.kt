package com.antlers.support.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.antlers.support.lexer.AntlersTokenTypes

class AntlersBraceMatcher : PairedBraceMatcher {
    companion object {
        private val PAIRS = arrayOf(
            BracePair(AntlersTokenTypes.ANTLERS_OPEN, AntlersTokenTypes.ANTLERS_CLOSE, true),
            BracePair(AntlersTokenTypes.COMMENT_OPEN, AntlersTokenTypes.COMMENT_CLOSE, true),
            BracePair(AntlersTokenTypes.PHP_RAW_OPEN, AntlersTokenTypes.PHP_RAW_CLOSE, true),
            BracePair(AntlersTokenTypes.PHP_ECHO_OPEN, AntlersTokenTypes.PHP_ECHO_CLOSE, true),
            BracePair(AntlersTokenTypes.LPAREN, AntlersTokenTypes.RPAREN, false),
            BracePair(AntlersTokenTypes.LBRACKET, AntlersTokenTypes.RBRACKET, false),
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?
    ): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset
}
