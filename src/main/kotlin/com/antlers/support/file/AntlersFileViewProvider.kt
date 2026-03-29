package com.antlers.support.file

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.antlers.support.AntlersLanguage
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.parser.AntlersParserDefinition
import java.util.concurrent.ConcurrentHashMap

class AntlersFileViewProvider(
    manager: PsiManager,
    virtualFile: VirtualFile,
    eventSystemEnabled: Boolean,
    private val templateLanguage: Language
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled),
    ConfigurableTemplateLanguageFileViewProvider {

    companion object {
        private val templateDataElementTypeCache = ConcurrentHashMap<String, TemplateDataElementType>()

        private fun getTemplateDataElementType(lang: Language): TemplateDataElementType {
            return templateDataElementTypeCache.computeIfAbsent(lang.id) {
                TemplateDataElementType(
                    "ANTLERS_TEMPLATE_DATA",
                    AntlersLanguage.INSTANCE,
                    AntlersTokenTypes.TEMPLATE_TEXT,
                    AntlersOuterElementType("ANTLERS_FRAGMENT")
                )
            }
        }
    }

    override fun getBaseLanguage(): Language = AntlersLanguage.INSTANCE

    override fun getTemplateDataLanguage(): Language = templateLanguage

    override fun getLanguages(): Set<Language> = setOf(baseLanguage, templateDataLanguage)

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return AntlersFileViewProvider(manager, fileCopy, false, templateLanguage)
    }

    override fun createFile(lang: Language): PsiFile? {
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang) ?: return null

        return if (lang.isKindOf(templateDataLanguage)) {
            val file = parserDefinition.createFile(this) as PsiFileImpl
            file.contentElementType = getTemplateDataElementType(lang)
            file
        } else if (lang.isKindOf(baseLanguage)) {
            parserDefinition.createFile(this)
        } else {
            null
        }
    }

    override fun supportsIncrementalReparse(rootLanguage: Language): Boolean = false
}

class AntlersOuterElementType(debugName: String) :
    com.intellij.psi.tree.IElementType(debugName, AntlersLanguage.INSTANCE)
