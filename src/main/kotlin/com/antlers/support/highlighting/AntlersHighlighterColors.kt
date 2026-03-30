package com.antlers.support.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object AntlersHighlighterColors {
    val DELIMITER: TextAttributesKey =
        createTextAttributesKey("ANTLERS_DELIMITER", DefaultLanguageHighlighterColors.BRACES)

    val KEYWORD: TextAttributesKey =
        createTextAttributesKey("ANTLERS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

    val IDENTIFIER: TextAttributesKey =
        createTextAttributesKey("ANTLERS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)

    val TAG_NAME: TextAttributesKey =
        createTextAttributesKey("ANTLERS_TAG_NAME", DefaultLanguageHighlighterColors.FUNCTION_CALL)

    val OPERATOR: TextAttributesKey =
        createTextAttributesKey("ANTLERS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    val PIPE: TextAttributesKey =
        createTextAttributesKey("ANTLERS_PIPE", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    val STRING: TextAttributesKey =
        createTextAttributesKey("ANTLERS_STRING", DefaultLanguageHighlighterColors.STRING)

    val NUMBER: TextAttributesKey =
        createTextAttributesKey("ANTLERS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)

    val COMMENT: TextAttributesKey =
        createTextAttributesKey("ANTLERS_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)

    val PHP_CONTENT: TextAttributesKey =
        createTextAttributesKey("ANTLERS_PHP_CONTENT", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR)

    val PARAMETER: TextAttributesKey =
        createTextAttributesKey("ANTLERS_PARAMETER", DefaultLanguageHighlighterColors.METADATA)

    val PUNCTUATION: TextAttributesKey =
        createTextAttributesKey("ANTLERS_PUNCTUATION", DefaultLanguageHighlighterColors.DOT)

    val BAD_CHARACTER: TextAttributesKey =
        createTextAttributesKey("ANTLERS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
}
