package com.antlers.support.parser

import com.intellij.psi.tree.IElementType
import com.antlers.support.AntlersLanguage

class AntlersElementType(debugName: String) : IElementType(debugName, AntlersLanguage.INSTANCE)
