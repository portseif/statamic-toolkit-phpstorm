package com.antlers.support.file

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.tree.OuterLanguageElementType
import com.antlers.support.AntlersLanguage
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.partials.AntlersPartialReferenceSupport
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
                    OuterLanguageElementType("ANTLERS_FRAGMENT", AntlersLanguage.INSTANCE)
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

    override fun findElementAt(offset: Int): PsiElement? {
        // Prefer the template data (HTML) tree for offsets in HTML content.
        // This enables CSS class navigation, JS references, etc.
        val templateFile = getPsi(templateDataLanguage)
        if (templateFile != null) {
            val element = AbstractFileViewProvider.findElementAt(templateFile, offset)
            if (element != null && element.node?.elementType !is OuterLanguageElementType) {
                return element
            }
        }
        return super.findElementAt(offset)
    }

    override fun findReferenceAt(offset: Int): PsiReference? {
        // Check the template data (HTML) tree first for CSS/JS references
        val templateFile = getPsi(templateDataLanguage)
        if (templateFile != null) {
            val ref = AbstractFileViewProvider.findReferenceAt(templateFile, offset)
            if (ref != null) return ref
        }

        val antlersFile = getPsi(baseLanguage)
        if (antlersFile != null) {
            val element = AbstractFileViewProvider.findElementAt(antlersFile, offset)
            if (element != null) {
                val partialReference = AntlersPartialReferenceSupport.referenceForElement(element)
                if (partialReference != null) return partialReference
            }
        }
        return super.findReferenceAt(offset)
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
