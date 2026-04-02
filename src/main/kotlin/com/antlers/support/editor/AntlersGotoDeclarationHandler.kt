package com.antlers.support.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.PsiTreeUtil
import com.antlers.support.AntlersLanguage
import com.antlers.support.actions.StatamicSnippetTemplates
import com.antlers.support.psi.AntlersModifier
import com.antlers.support.psi.AntlersTagExpression
import com.antlers.support.psi.AntlersTagName
import com.antlers.support.settings.AntlersSettings
import com.antlers.support.statamic.StatamicCatalog

class AntlersGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val virtualFile = antlersVirtualFile(sourceElement) ?: return null
        if (!virtualFile.name.contains(".antlers.")) return null

        val project = sourceElement.project
        val settings = AntlersSettings.getInstance().state

        // Get the element from the Antlers PSI tree explicitly,
        // since the platform may give us an element from the HTML tree
        val viewProvider = InjectedLanguageManager.getInstance(project)
            .getTopLevelFile(sourceElement)
            .viewProvider
        val antlersFile = viewProvider.getPsi(AntlersLanguage.INSTANCE) ?: return null
        val antlersElement = antlersFile.findElementAt(offset) ?: return null

        // 1. Custom tag navigation — Cmd-click tag name → app/Tags/ClassName.php
        if (settings.enableCustomTagNavigation) {
            val tagTargets = resolveCustomTag(antlersElement, project)
            if (tagTargets != null) return tagTargets.toTypedArray()

            // 2. Custom modifier navigation — Cmd-click modifier name → app/Modifiers/ClassName.php
            val modTargets = resolveCustomModifier(antlersElement, project)
            if (modTargets != null) return modTargets.toTypedArray()
        }

        return null
    }

    private fun antlersVirtualFile(sourceElement: PsiElement): VirtualFile? {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(sourceElement.project)
        return injectedLanguageManager.getTopLevelFile(sourceElement).virtualFile
            ?: sourceElement.containingFile?.virtualFile
    }

    // -------------------------------------------------------------------------
    // Custom tag resolution
    // -------------------------------------------------------------------------

    private fun resolveCustomTag(element: PsiElement, project: Project): List<PsiElement>? {
        // Check if the element is inside an AntlersTagName
        val tagName = PsiTreeUtil.getParentOfType(element, AntlersTagName::class.java)
            ?: if (element.parent is AntlersTagName) element.parent as AntlersTagName else null
            ?: return null

        val name = tagName.text ?: return null
        val rootName = name.substringBefore(':')

        // Skip built-in Statamic tags
        if (StatamicCatalog.isKnownTag(name)) return null

        // Skip partials — handled by partial resolution
        if (rootName == "partial") return null

        val className = StatamicSnippetTemplates.normalizeTagClassName(rootName) ?: return null
        val targets = findPhpFile(project, className, "app/Tags")
        return if (targets.isNotEmpty()) targets else null
    }

    // -------------------------------------------------------------------------
    // Custom modifier resolution (new)
    // -------------------------------------------------------------------------

    private fun resolveCustomModifier(element: PsiElement, project: Project): List<PsiElement>? {
        // Check if the element is the identifier inside an AntlersModifier
        val modifier = PsiTreeUtil.getParentOfType(element, AntlersModifier::class.java)
            ?: return null

        val modifierName = modifier.identifier?.text ?: return null

        // Skip built-in Statamic modifiers
        if (StatamicCatalog.findModifier(modifierName) != null) return null

        val className = StatamicSnippetTemplates.normalizeModifierClassName(modifierName) ?: return null
        val targets = findPhpFile(project, className, "app/Modifiers")
        return if (targets.isNotEmpty()) targets else null
    }

    // -------------------------------------------------------------------------
    // Shared PHP file lookup
    // -------------------------------------------------------------------------

    private fun findPhpFile(project: Project, className: String, subDir: String): List<PsiElement> {
        val basePath = project.basePath ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)

        // Try directory-scoped search first for performance
        val dir = LocalFileSystem.getInstance().findFileByPath("$basePath/$subDir")
        val scope = if (dir != null) {
            GlobalSearchScopesCore.directoryScope(project, dir, true)
        } else {
            GlobalSearchScope.projectScope(project)
        }

        val fileName = "$className.php"
        val results = mutableListOf<PsiElement>()
        val files = FilenameIndex.getVirtualFilesByName(fileName, scope)
        for (file in files) {
            psiManager.findFile(file)?.let { results.add(it) }
        }
        return results
    }
}
