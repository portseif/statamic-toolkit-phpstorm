package com.antlers.support

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class AntlersFileType private constructor() : LanguageFileType(AntlersLanguage.INSTANCE) {
    companion object {
        @JvmField
        val INSTANCE = AntlersFileType()
    }

    override fun getName(): String = "Antlers"

    override fun getDescription(): String = "Antlers template file"

    override fun getDefaultExtension(): String = "antlers.html"

    override fun getIcon(): Icon = AntlersIcons.FILE
}
