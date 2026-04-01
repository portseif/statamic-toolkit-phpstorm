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
        if (toolId != XML_UNBOUND_NS_PREFIX_INSPECTION_ID) return false
        if (!isProviderAvailable(element.containingFile ?: return false)) return false

        val attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
            ?: (element as? XmlAttribute)
            ?: return false

        return isAlpinePseudoNamespaceAttribute(attribute.name)
    }

    override fun suppressForFile(element: PsiElement, toolId: String) = Unit

    override fun suppressForTag(element: PsiElement, toolId: String) = Unit

    private fun isAlpinePseudoNamespaceAttribute(attributeName: String): Boolean {
        val prefix = attributeName.substringBefore(':', missingDelimiterValue = "")
        return prefix in ALPINE_PSEUDO_NAMESPACE_PREFIXES
    }

    private companion object {
        const val XML_UNBOUND_NS_PREFIX_INSPECTION_ID = "XmlUnboundNsPrefix"

        val ALPINE_PSEUDO_NAMESPACE_PREFIXES = setOf(
            "x-bind",
            "x-on",
            "x-transition"
        )
    }
}
