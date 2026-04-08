package com.antlers.support.inspections

import com.antlers.support.AntlersLanguage
import com.intellij.codeInspection.XmlSuppressionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute

class AntlersAlpineXmlSuppressionProvider : XmlSuppressionProvider() {
    override fun isProviderAvailable(file: PsiFile): Boolean {
        return file.viewProvider.baseLanguage == AntlersLanguage.INSTANCE
    }

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in SUPPRESSED_INSPECTION_IDS) return false
        if (!isProviderAvailable(element.containingFile ?: return false)) return false

        val attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
            ?: (element as? XmlAttribute)
            ?: return false

        return isAlpineAttribute(attribute.name)
    }

    override fun suppressForFile(element: PsiElement, toolId: String) = Unit

    override fun suppressForTag(element: PsiElement, toolId: String) = Unit

    private fun isAlpineAttribute(attributeName: String): Boolean {
        // Shorthand : and @ prefixes (e.g. :style, :class, :key, @click, @submit)
        if (attributeName.startsWith(":") || attributeName.startsWith("@")) return true

        // Any x- prefixed attribute (x-data, x-show, x-bind:foo, x-on:click, x-transition:enter, etc.)
        if (attributeName.startsWith("x-")) return true

        return false
    }

    private companion object {
        val SUPPRESSED_INSPECTION_IDS = setOf(
            "XmlUnboundNsPrefix",
            "HtmlUnknownAttribute",
            "HtmlUnknownTag",
            "XmlUnresolvedReference",
            "CheckTagEmptyBody",
        )
    }
}
