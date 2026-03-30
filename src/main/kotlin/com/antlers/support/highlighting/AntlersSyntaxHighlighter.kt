package com.antlers.support.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.antlers.support.lexer.AntlersLexerAdapter
import com.antlers.support.lexer.AntlersTokenSets
import com.antlers.support.lexer.AntlersTokenTypes

class AntlersSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = AntlersLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when {
            // Delimiters
            tokenType == AntlersTokenTypes.ANTLERS_OPEN ||
            tokenType == AntlersTokenTypes.ANTLERS_CLOSE ||
            tokenType == AntlersTokenTypes.TAG_SELF_CLOSE -> pack(AntlersHighlighterColors.DELIMITER)

            // Comments
            tokenType == AntlersTokenTypes.COMMENT_OPEN ||
            tokenType == AntlersTokenTypes.COMMENT_CLOSE ||
            tokenType == AntlersTokenTypes.COMMENT_CONTENT -> pack(AntlersHighlighterColors.COMMENT)

            // PHP regions
            tokenType == AntlersTokenTypes.PHP_RAW_OPEN ||
            tokenType == AntlersTokenTypes.PHP_RAW_CLOSE ||
            tokenType == AntlersTokenTypes.PHP_ECHO_OPEN ||
            tokenType == AntlersTokenTypes.PHP_ECHO_CLOSE -> pack(AntlersHighlighterColors.DELIMITER)

            tokenType == AntlersTokenTypes.PHP_RAW_CONTENT ||
            tokenType == AntlersTokenTypes.PHP_ECHO_CONTENT -> pack(AntlersHighlighterColors.PHP_CONTENT)

            // Keywords
            AntlersTokenSets.KEYWORDS.contains(tokenType) -> pack(AntlersHighlighterColors.KEYWORD)

            // Identifiers
            tokenType == AntlersTokenTypes.IDENTIFIER -> pack(AntlersHighlighterColors.IDENTIFIER)

            // Strings
            tokenType == AntlersTokenTypes.STRING_DQ ||
            tokenType == AntlersTokenTypes.STRING_SQ -> pack(AntlersHighlighterColors.STRING)

            // Numbers
            AntlersTokenSets.NUMBERS.contains(tokenType) -> pack(AntlersHighlighterColors.NUMBER)

            // Pipe modifier
            tokenType == AntlersTokenTypes.OP_PIPE -> pack(AntlersHighlighterColors.PIPE)

            // Operators
            AntlersTokenSets.OPERATORS.contains(tokenType) -> pack(AntlersHighlighterColors.OPERATOR)

            // Punctuation (colon, dot, comma, semicolon, brackets)
            tokenType == AntlersTokenTypes.COLON ||
            tokenType == AntlersTokenTypes.DOT ||
            tokenType == AntlersTokenTypes.COMMA ||
            tokenType == AntlersTokenTypes.SEMICOLON ||
            tokenType == AntlersTokenTypes.LPAREN ||
            tokenType == AntlersTokenTypes.RPAREN ||
            tokenType == AntlersTokenTypes.LBRACKET ||
            tokenType == AntlersTokenTypes.RBRACKET -> pack(AntlersHighlighterColors.PUNCTUATION)

            // Bad character
            tokenType == AntlersTokenTypes.BAD_CHARACTER -> pack(AntlersHighlighterColors.BAD_CHARACTER)

            else -> TextAttributesKey.EMPTY_ARRAY
        }
    }
}
