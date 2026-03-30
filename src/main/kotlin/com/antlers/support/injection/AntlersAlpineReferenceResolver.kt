package com.antlers.support.injection

import com.antlers.support.AntlersLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.impl.JSLocalImplicitElementImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag

object AntlersAlpineReferenceResolver {
    fun resolve(reference: JSReferenceExpression): PsiElement? {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(reference.project)
        val topLevelFile = injectedLanguageManager.getTopLevelFile(reference)
        if (topLevelFile.viewProvider.baseLanguage != AntlersLanguage.INSTANCE) return null

        val referenceName = reference.referenceNameElement?.text ?: return null
        val qualifierText = reference.qualifier?.text
        if (qualifierText != null && qualifierText != "this") return null

        val host = injectedLanguageManager.getInjectionHost(reference) as? XmlAttributeValue ?: return null
        var tag = PsiTreeUtil.getParentOfType(host, XmlTag::class.java, false)

        while (tag != null) {
            val loopVariable = tag.getAttribute("x-for")
                ?.valueElement
                ?.let { findLoopVariable(it, referenceName) }
            if (loopVariable != null) {
                return loopVariable
            }

            val xDataValue = tag.getAttribute("x-data")?.valueElement
            val property = xDataValue?.let { findProperty(it, referenceName, injectedLanguageManager) }
            if (property != null) {
                return property.nameIdentifier
                    ?: property.tryGetFunctionInitializer()?.nameIdentifier
                    ?: property
            }

            tag = tag.parentTag
        }

        return null
    }

    private fun findLoopVariable(xForValue: XmlAttributeValue, referenceName: String): PsiElement? {
        val expression = xForValue.value.trim()
        val inIndex = expression.indexOf(" in ")
        if (inIndex <= 0) return null

        val aliases = expression.substring(0, inIndex)
            .trim()
            .removePrefix("(")
            .removeSuffix(")")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (referenceName !in aliases) return null

        val typeString = if (aliases.indexOf(referenceName) == 1) "number" else "any"
        return JSLocalImplicitElementImpl(referenceName, typeString, xForValue)
    }

    private fun findProperty(
        xDataValue: XmlAttributeValue,
        propertyName: String,
        injectedLanguageManager: InjectedLanguageManager
    ): JSProperty? {
        val injectedRoot = injectedLanguageManager.getInjectedPsiFiles(xDataValue)
            ?.firstOrNull()
            ?.first
            ?: return null

        val objectLiteral = PsiTreeUtil.findChildOfType(
            injectedRoot,
            JSObjectLiteralExpression::class.java
        ) ?: return null

        return objectLiteral.findProperty(propertyName)
    }
}
