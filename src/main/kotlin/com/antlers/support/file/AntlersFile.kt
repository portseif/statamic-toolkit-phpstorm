package com.antlers.support.file

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.antlers.support.AntlersFileType
import com.antlers.support.AntlersLanguage

class AntlersFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, AntlersLanguage.INSTANCE) {
    override fun getFileType(): FileType = AntlersFileType.INSTANCE

    override fun toString(): String = "Antlers File"
}
