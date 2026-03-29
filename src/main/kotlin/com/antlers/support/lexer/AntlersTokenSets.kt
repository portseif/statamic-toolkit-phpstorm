package com.antlers.support.lexer

import com.intellij.psi.tree.TokenSet

object AntlersTokenSets {
    @JvmField
    val COMMENTS: TokenSet = TokenSet.create(
        AntlersTokenTypes.COMMENT_OPEN,
        AntlersTokenTypes.COMMENT_CONTENT,
        AntlersTokenTypes.COMMENT_CLOSE
    )

    @JvmField
    val STRINGS: TokenSet = TokenSet.create(
        AntlersTokenTypes.STRING_DQ,
        AntlersTokenTypes.STRING_SQ
    )

    @JvmField
    val KEYWORDS: TokenSet = TokenSet.create(
        AntlersTokenTypes.KEYWORD_IF,
        AntlersTokenTypes.KEYWORD_ELSEIF,
        AntlersTokenTypes.KEYWORD_ELSE,
        AntlersTokenTypes.KEYWORD_ENDIF,
        AntlersTokenTypes.KEYWORD_UNLESS,
        AntlersTokenTypes.KEYWORD_ENDUNLESS,
        AntlersTokenTypes.KEYWORD_SWITCH,
        AntlersTokenTypes.KEYWORD_NOPARSE,
        AntlersTokenTypes.KEYWORD_ONCE,
        AntlersTokenTypes.KEYWORD_TRUE,
        AntlersTokenTypes.KEYWORD_FALSE,
        AntlersTokenTypes.KEYWORD_NULL,
        AntlersTokenTypes.KEYWORD_VOID,
        AntlersTokenTypes.KEYWORD_AS,
        AntlersTokenTypes.KEYWORD_AND,
        AntlersTokenTypes.KEYWORD_OR,
        AntlersTokenTypes.KEYWORD_XOR,
        AntlersTokenTypes.KEYWORD_NOT,
        AntlersTokenTypes.KEYWORD_WHERE,
        AntlersTokenTypes.KEYWORD_ORDERBY,
        AntlersTokenTypes.KEYWORD_GROUPBY,
        AntlersTokenTypes.KEYWORD_MERGE,
        AntlersTokenTypes.KEYWORD_TAKE,
        AntlersTokenTypes.KEYWORD_SKIP,
        AntlersTokenTypes.KEYWORD_PLUCK
    )

    @JvmField
    val NUMBERS: TokenSet = TokenSet.create(
        AntlersTokenTypes.NUMBER_INT,
        AntlersTokenTypes.NUMBER_FLOAT
    )

    @JvmField
    val OPERATORS: TokenSet = TokenSet.create(
        AntlersTokenTypes.OP_IDENTICAL,
        AntlersTokenTypes.OP_EQUALS,
        AntlersTokenTypes.OP_NOT_IDENTICAL,
        AntlersTokenTypes.OP_NOT_EQUALS,
        AntlersTokenTypes.OP_SPACESHIP,
        AntlersTokenTypes.OP_GREATER_EQUAL,
        AntlersTokenTypes.OP_LESS_EQUAL,
        AntlersTokenTypes.OP_GREATER,
        AntlersTokenTypes.OP_LESS,
        AntlersTokenTypes.OP_AND,
        AntlersTokenTypes.OP_OR,
        AntlersTokenTypes.OP_NOT,
        AntlersTokenTypes.OP_NULL_COALESCE,
        AntlersTokenTypes.OP_GATEKEEPER,
        AntlersTokenTypes.OP_TERNARY_QUESTION,
        AntlersTokenTypes.OP_POWER,
        AntlersTokenTypes.OP_PLUS_ASSIGN,
        AntlersTokenTypes.OP_MINUS_ASSIGN,
        AntlersTokenTypes.OP_MULTIPLY_ASSIGN,
        AntlersTokenTypes.OP_DIVIDE_ASSIGN,
        AntlersTokenTypes.OP_MODULO_ASSIGN,
        AntlersTokenTypes.OP_ASSIGN,
        AntlersTokenTypes.OP_PLUS,
        AntlersTokenTypes.OP_MINUS,
        AntlersTokenTypes.OP_MULTIPLY,
        AntlersTokenTypes.OP_DIVIDE,
        AntlersTokenTypes.OP_MODULO,
        AntlersTokenTypes.OP_PIPE,
        AntlersTokenTypes.OP_ARROW,
        AntlersTokenTypes.OP_RANGE
    )

    @JvmField
    val DELIMITERS: TokenSet = TokenSet.create(
        AntlersTokenTypes.ANTLERS_OPEN,
        AntlersTokenTypes.ANTLERS_CLOSE,
        AntlersTokenTypes.COMMENT_OPEN,
        AntlersTokenTypes.COMMENT_CLOSE,
        AntlersTokenTypes.PHP_RAW_OPEN,
        AntlersTokenTypes.PHP_RAW_CLOSE,
        AntlersTokenTypes.PHP_ECHO_OPEN,
        AntlersTokenTypes.PHP_ECHO_CLOSE
    )
}
