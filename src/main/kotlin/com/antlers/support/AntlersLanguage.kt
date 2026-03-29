package com.antlers.support

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

class AntlersLanguage private constructor() : Language("Antlers"), TemplateLanguage {
    companion object {
        @JvmField
        val INSTANCE = AntlersLanguage()
    }

    override fun getDisplayName(): String = "Antlers"
}
