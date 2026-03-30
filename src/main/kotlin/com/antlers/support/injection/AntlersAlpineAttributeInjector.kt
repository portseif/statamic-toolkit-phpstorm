package com.antlers.support.injection

import com.antlers.support.AntlersLanguage
import com.antlers.support.settings.AntlersSettings
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

class AntlersAlpineAttributeInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (!AntlersSettings.getInstance().state.enableAlpineJsInjection) return

        val host = context as? XmlAttributeValue ?: return
        val attribute = host.parent as? XmlAttribute ?: return
        val spec = injectionSpec(attribute.name) ?: return

        val viewProvider = host.containingFile.viewProvider
        if (viewProvider.baseLanguage != AntlersLanguage.INSTANCE) return

        val jsLanguage = Language.findLanguageByID("JavaScript") ?: return
        val range = ElementManipulators.getValueTextRange(host)
        if (range.isEmpty) return

        registrar
            .startInjecting(jsLanguage)
            .addPlace(spec.prefix, spec.suffix, host as PsiLanguageInjectionHost, range)
            .doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(XmlAttributeValue::class.java)
    }

    private fun injectionSpec(attributeName: String): InjectionSpec? {
        return when {
            attributeName == "x-data" -> EXPRESSION
            attributeName == "x-for" -> null
            attributeName in expressionAttributes -> EXPRESSION
            attributeName.startsWith("x-bind:") -> EXPRESSION
            attributeName.startsWith(":") -> EXPRESSION
            attributeName in statementAttributes -> STATEMENT
            attributeName.startsWith("x-on:") -> STATEMENT
            attributeName.startsWith("@") -> STATEMENT
            else -> null
        }
    }

    private data class InjectionSpec(val prefix: String?, val suffix: String?)

    companion object {
        private val EXPRESSION = InjectionSpec("(", ")")
        private val STATEMENT = InjectionSpec(null, null)

        private val expressionAttributes = setOf(
            "x-show",
            "x-if",
            "x-text",
            "x-html",
            "x-model",
            "x-id"
        )

        private val statementAttributes = setOf(
            "x-init",
            "x-effect"
        )
    }
}
