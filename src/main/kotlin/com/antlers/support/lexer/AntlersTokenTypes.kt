package com.antlers.support.lexer

import com.intellij.psi.tree.IElementType
import com.antlers.support.AntlersLanguage

class AntlersTokenType(debugName: String) : IElementType(debugName, AntlersLanguage.INSTANCE) {
    override fun toString(): String = "AntlersTokenType.${super.toString()}"
}

object AntlersTokenTypes {
    // Delimiters
    @JvmField val ANTLERS_OPEN = AntlersTokenType("ANTLERS_OPEN")           // {{
    @JvmField val ANTLERS_CLOSE = AntlersTokenType("ANTLERS_CLOSE")         // }}
    @JvmField val COMMENT_OPEN = AntlersTokenType("COMMENT_OPEN")           // {{#
    @JvmField val COMMENT_CLOSE = AntlersTokenType("COMMENT_CLOSE")         // #}}
    @JvmField val PHP_RAW_OPEN = AntlersTokenType("PHP_RAW_OPEN")           // {{?
    @JvmField val PHP_RAW_CLOSE = AntlersTokenType("PHP_RAW_CLOSE")         // ?}}
    @JvmField val PHP_ECHO_OPEN = AntlersTokenType("PHP_ECHO_OPEN")         // {{$
    @JvmField val PHP_ECHO_CLOSE = AntlersTokenType("PHP_ECHO_CLOSE")       // $}}

    // Content regions
    @JvmField val TEMPLATE_TEXT = AntlersTokenType("TEMPLATE_TEXT")           // HTML passthrough
    @JvmField val COMMENT_CONTENT = AntlersTokenType("COMMENT_CONTENT")
    @JvmField val PHP_RAW_CONTENT = AntlersTokenType("PHP_RAW_CONTENT")
    @JvmField val PHP_ECHO_CONTENT = AntlersTokenType("PHP_ECHO_CONTENT")
    @JvmField val NOPARSE_CONTENT = AntlersTokenType("NOPARSE_CONTENT")
    @JvmField val ESCAPED_CONTENT = AntlersTokenType("ESCAPED_CONTENT")      // @{{ ... }}

    // Keywords
    @JvmField val KEYWORD_IF = AntlersTokenType("KEYWORD_IF")
    @JvmField val KEYWORD_ELSEIF = AntlersTokenType("KEYWORD_ELSEIF")
    @JvmField val KEYWORD_ELSE = AntlersTokenType("KEYWORD_ELSE")
    @JvmField val KEYWORD_ENDIF = AntlersTokenType("KEYWORD_ENDIF")
    @JvmField val KEYWORD_UNLESS = AntlersTokenType("KEYWORD_UNLESS")
    @JvmField val KEYWORD_ENDUNLESS = AntlersTokenType("KEYWORD_ENDUNLESS")
    @JvmField val KEYWORD_SWITCH = AntlersTokenType("KEYWORD_SWITCH")
    @JvmField val KEYWORD_NOPARSE = AntlersTokenType("KEYWORD_NOPARSE")
    @JvmField val KEYWORD_ONCE = AntlersTokenType("KEYWORD_ONCE")
    @JvmField val KEYWORD_TRUE = AntlersTokenType("KEYWORD_TRUE")
    @JvmField val KEYWORD_FALSE = AntlersTokenType("KEYWORD_FALSE")
    @JvmField val KEYWORD_NULL = AntlersTokenType("KEYWORD_NULL")
    @JvmField val KEYWORD_VOID = AntlersTokenType("KEYWORD_VOID")
    @JvmField val KEYWORD_AS = AntlersTokenType("KEYWORD_AS")
    @JvmField val KEYWORD_AND = AntlersTokenType("KEYWORD_AND")
    @JvmField val KEYWORD_OR = AntlersTokenType("KEYWORD_OR")
    @JvmField val KEYWORD_XOR = AntlersTokenType("KEYWORD_XOR")
    @JvmField val KEYWORD_NOT = AntlersTokenType("KEYWORD_NOT")
    @JvmField val KEYWORD_WHERE = AntlersTokenType("KEYWORD_WHERE")
    @JvmField val KEYWORD_ORDERBY = AntlersTokenType("KEYWORD_ORDERBY")
    @JvmField val KEYWORD_GROUPBY = AntlersTokenType("KEYWORD_GROUPBY")
    @JvmField val KEYWORD_MERGE = AntlersTokenType("KEYWORD_MERGE")
    @JvmField val KEYWORD_TAKE = AntlersTokenType("KEYWORD_TAKE")
    @JvmField val KEYWORD_SKIP = AntlersTokenType("KEYWORD_SKIP")
    @JvmField val KEYWORD_PLUCK = AntlersTokenType("KEYWORD_PLUCK")

    // Identifiers
    @JvmField val IDENTIFIER = AntlersTokenType("IDENTIFIER")

    // Literals
    @JvmField val STRING_DQ = AntlersTokenType("STRING_DQ")
    @JvmField val STRING_SQ = AntlersTokenType("STRING_SQ")
    @JvmField val NUMBER_INT = AntlersTokenType("NUMBER_INT")
    @JvmField val NUMBER_FLOAT = AntlersTokenType("NUMBER_FLOAT")

    // Operators
    @JvmField val OP_IDENTICAL = AntlersTokenType("OP_IDENTICAL")           // ===
    @JvmField val OP_EQUALS = AntlersTokenType("OP_EQUALS")                 // ==
    @JvmField val OP_NOT_IDENTICAL = AntlersTokenType("OP_NOT_IDENTICAL")   // !==
    @JvmField val OP_NOT_EQUALS = AntlersTokenType("OP_NOT_EQUALS")         // !=
    @JvmField val OP_SPACESHIP = AntlersTokenType("OP_SPACESHIP")           // <=>
    @JvmField val OP_GREATER_EQUAL = AntlersTokenType("OP_GREATER_EQUAL")   // >=
    @JvmField val OP_LESS_EQUAL = AntlersTokenType("OP_LESS_EQUAL")         // <=
    @JvmField val OP_GREATER = AntlersTokenType("OP_GREATER")               // >
    @JvmField val OP_LESS = AntlersTokenType("OP_LESS")                     // <
    @JvmField val OP_AND = AntlersTokenType("OP_AND")                       // &&
    @JvmField val OP_OR = AntlersTokenType("OP_OR")                         // ||
    @JvmField val OP_NOT = AntlersTokenType("OP_NOT")                       // !
    @JvmField val OP_NULL_COALESCE = AntlersTokenType("OP_NULL_COALESCE")   // ??
    @JvmField val OP_GATEKEEPER = AntlersTokenType("OP_GATEKEEPER")         // ?=
    @JvmField val OP_TERNARY_QUESTION = AntlersTokenType("OP_TERNARY_QUESTION") // ?
    @JvmField val OP_POWER = AntlersTokenType("OP_POWER")                   // **
    @JvmField val OP_PLUS_ASSIGN = AntlersTokenType("OP_PLUS_ASSIGN")       // +=
    @JvmField val OP_MINUS_ASSIGN = AntlersTokenType("OP_MINUS_ASSIGN")     // -=
    @JvmField val OP_MULTIPLY_ASSIGN = AntlersTokenType("OP_MULTIPLY_ASSIGN") // *=
    @JvmField val OP_DIVIDE_ASSIGN = AntlersTokenType("OP_DIVIDE_ASSIGN")   // /=
    @JvmField val OP_MODULO_ASSIGN = AntlersTokenType("OP_MODULO_ASSIGN")   // %=
    @JvmField val OP_ASSIGN = AntlersTokenType("OP_ASSIGN")                 // =
    @JvmField val OP_PLUS = AntlersTokenType("OP_PLUS")                     // +
    @JvmField val OP_MINUS = AntlersTokenType("OP_MINUS")                   // -
    @JvmField val OP_MULTIPLY = AntlersTokenType("OP_MULTIPLY")             // *
    @JvmField val OP_DIVIDE = AntlersTokenType("OP_DIVIDE")                 // /
    @JvmField val OP_MODULO = AntlersTokenType("OP_MODULO")                 // %
    @JvmField val OP_PIPE = AntlersTokenType("OP_PIPE")                     // |
    @JvmField val OP_ARROW = AntlersTokenType("OP_ARROW")                   // =>
    @JvmField val OP_RANGE = AntlersTokenType("OP_RANGE")                   // ..

    // Punctuation
    @JvmField val DOT = AntlersTokenType("DOT")
    @JvmField val COLON = AntlersTokenType("COLON")
    @JvmField val SEMICOLON = AntlersTokenType("SEMICOLON")
    @JvmField val COMMA = AntlersTokenType("COMMA")
    @JvmField val LPAREN = AntlersTokenType("LPAREN")
    @JvmField val RPAREN = AntlersTokenType("RPAREN")
    @JvmField val LBRACKET = AntlersTokenType("LBRACKET")
    @JvmField val RBRACKET = AntlersTokenType("RBRACKET")
    @JvmField val SLASH = AntlersTokenType("SLASH")
    @JvmField val TAG_SELF_CLOSE = AntlersTokenType("TAG_SELF_CLOSE")       // /}}
    @JvmField val PERCENT = AntlersTokenType("PERCENT")                     // %
    @JvmField val DOLLAR = AntlersTokenType("DOLLAR")                       // $
    @JvmField val AT = AntlersTokenType("AT")                               // @

    // Whitespace and errors
    @JvmField val WHITE_SPACE = com.intellij.psi.TokenType.WHITE_SPACE
    @JvmField val BAD_CHARACTER = com.intellij.psi.TokenType.BAD_CHARACTER
}
