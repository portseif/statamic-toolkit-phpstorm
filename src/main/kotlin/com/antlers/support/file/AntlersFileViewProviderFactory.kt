package com.antlers.support.file

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.LanguageSubstitutors
import com.intellij.psi.PsiManager
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.fileTypes.PlainTextLanguage

class AntlersFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean
    ): FileViewProvider {
        val mappedLanguage = TemplateDataLanguageMappings.getInstance(manager.project)
            .getMapping(file)
            ?: HTMLLanguage.INSTANCE

        val templateLang = if (mappedLanguage is TemplateLanguage) {
            PlainTextLanguage.INSTANCE
        } else {
            LanguageSubstitutors.getInstance()
                .substituteLanguage(mappedLanguage, file, manager.project)
        }

        return AntlersFileViewProvider(manager, file, eventSystemEnabled, templateLang)
    }
}
