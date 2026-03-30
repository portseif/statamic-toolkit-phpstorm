package com.antlers.support.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.antlers.support.lexer.AntlersTokenTypes

class AntlersQuoteHandler : SimpleTokenSetQuoteHandler(
    AntlersTokenTypes.STRING_DQ,
    AntlersTokenTypes.STRING_SQ
)
