package com.antlers.support.documentation

import com.antlers.support.AntlersLanguage
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.psi.AntlersModifier
import com.antlers.support.psi.AntlersTagExpression
import com.antlers.support.psi.AntlersTagName
import com.antlers.support.settings.AntlersSettings
import com.antlers.support.statamic.StatamicCatalog
import com.antlers.support.statamic.StatamicDocItem
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class AntlersDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        val antlersFile = file.viewProvider.getPsi(AntlersLanguage.INSTANCE) ?: file
        val element = antlersFile.findElementAt(targetOffset) ?: contextElement ?: return null

        val modifier = when {
            element is AntlersModifier -> element
            element.parent is AntlersModifier -> element.parent as AntlersModifier
            else -> null
        }
        if (modifier != null && StatamicCatalog.findModifier(modifier.identifier?.text.orEmpty()) != null) {
            return modifier
        }

        val tagName = when {
            element is AntlersTagName -> element
            element.parent is AntlersTagName -> element.parent as AntlersTagName
            else -> null
        } ?: return null

        return if (resolveNameDocItem(element, tagName) != null) tagName else null
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (!AntlersSettings.getInstance().state.enableHoverDocumentation) return null
        val resolved = resolveDocItem(element) ?: resolveDocItem(originalElement) ?: return null
        return renderDoc(resolved.item, resolved.kind)
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        if (!AntlersSettings.getInstance().state.enableHoverDocumentation) return null
        return generateDoc(element, originalElement)
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val resolved = resolveDocItem(element) ?: resolveDocItem(originalElement) ?: return null
        return "${resolved.kind} ${resolved.item.name}"
    }

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): MutableList<String>? {
        return null
    }

    private fun resolveDocItem(element: PsiElement?): ResolvedDoc? {
        if (element == null) return null

        val modifier = when {
            element is AntlersModifier -> element
            element.parent is AntlersModifier -> element.parent as AntlersModifier
            else -> null
        }
        if (modifier != null && (modifier.identifier == element || element is AntlersModifier)) {
            val item = StatamicCatalog.findModifier(modifier.identifier?.text.orEmpty()) ?: return null
            return ResolvedDoc(item, "Statamic Modifier")
        }

        val tagName = when {
            element is AntlersTagName -> element
            element.parent is AntlersTagName -> element.parent as AntlersTagName
            else -> null
        } ?: return null

        return resolveNameDocItem(element, tagName)
    }

    private fun resolveNameDocItem(element: PsiElement, tagName: AntlersTagName): ResolvedDoc? {
        val fullName = tagName.text
        if (element.node?.elementType != AntlersTokenTypes.IDENTIFIER && element !is AntlersTagName) {
            return null
        }

        val expression = tagName.parent as? AntlersTagExpression
        val exactVariable = StatamicCatalog.findVariable(fullName)
        if (shouldPreferVariable(tagName, expression, exactVariable)) {
            return ResolvedDoc(exactVariable!!, "Statamic Variable")
        }

        StatamicCatalog.findTag(fullName)?.let { return ResolvedDoc(it, "Statamic Tag") }
        exactVariable?.let { return ResolvedDoc(it, "Statamic Variable") }

        if (element is AntlersTagName || element.prevSibling == null) {
            val rootName = fullName.substringBefore(':').substringBefore('/')
            StatamicCatalog.findTag(rootName)?.let { return ResolvedDoc(it, "Statamic Tag") }
            StatamicCatalog.findVariable(rootName)?.let { return ResolvedDoc(it, "Statamic Variable") }
        }

        return null
    }

    private fun shouldPreferVariable(
        tagName: AntlersTagName,
        expression: AntlersTagExpression?,
        variable: StatamicDocItem?
    ): Boolean {
        if (variable == null) return false
        if (tagName.text.contains(':') || tagName.text.contains('/')) return false
        return expression?.parameterList?.isEmpty() != false
    }

    private fun renderDoc(item: StatamicDocItem, kind: String): String {
        val builder = StringBuilder()
        builder.append(DocumentationMarkup.DEFINITION_START)
        builder.append(kind)
        builder.append(" <code>")
        builder.append(StringUtil.escapeXmlEntities(item.displayName))
        builder.append("</code>")
        builder.append(DocumentationMarkup.DEFINITION_END)

        builder.append(DocumentationMarkup.CONTENT_START)
        builder.append(StringUtil.escapeXmlEntities(item.description))
        builder.append(DocumentationMarkup.CONTENT_END)

        builder.append(DocumentationMarkup.SECTIONS_START)
        item.example?.let {
            addSection(
                builder,
                "Example",
                "<pre>${StringUtil.escapeXmlEntities(it)}</pre>"
            )
        }
        builder.append(DocumentationMarkup.SECTIONS_END)

        val escapedName = StringUtil.escapeXmlEntities(item.name)
        val escapedUrl = StringUtil.escapeXmlEntities(item.url)
        builder.append("<p><a href=\"$escapedUrl\"><code>$escapedName</code> on statamic.dev</a></p>")

        return builder.toString()
    }

    private fun addSection(builder: StringBuilder, key: String, value: String) {
        builder.append(DocumentationMarkup.SECTION_HEADER_START)
        builder.append(key)
        builder.append(DocumentationMarkup.SECTION_SEPARATOR)
        builder.append(value)
        builder.append(DocumentationMarkup.SECTION_END)
    }

    private data class ResolvedDoc(
        val item: StatamicDocItem,
        val kind: String
    )
}
