package com.antlers.support.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.antlers.support.AntlersIcons
import com.antlers.support.AntlersLanguage
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.partials.AntlersPartialPaths
import com.antlers.support.settings.AntlersSettings

class AntlersCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(AntlersLanguage.INSTANCE),
            AntlersCompletionProvider()
        )
    }
}

private class AntlersCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val settings = AntlersSettings.getInstance().state

        val prevLeaf = PsiTreeUtil.prevVisibleLeaf(position)
        val prevType = prevLeaf?.node?.elementType

        // After | pipe — suggest modifiers
        if (prevType == AntlersTokenTypes.OP_PIPE) {
            if (!settings.enableModifierCompletion) return
            StatamicData.MODIFIERS.forEach { item ->
                result.addElement(
                    LookupElementBuilder.create(item.name)
                        .withTypeText(item.description, true)
                        .withIcon(AntlersIcons.FILE)
                        .bold()
                )
            }
            return
        }

        // After colon — check context for tag scopes or partials
        if (prevType == AntlersTokenTypes.COLON) {
            val colonPrev = PsiTreeUtil.prevVisibleLeaf(prevLeaf!!)
            val tagName = colonPrev?.text

            // After partial: — suggest partial file paths
            if (tagName == "partial") {
                addPartialCompletions(parameters, result)
                return
            }

            // After tag: — suggest sub-tags
            if (tagName != null && settings.enableTagCompletion) {
                val subTags = StatamicData.getSubTags(tagName)
                if (subTags.isNotEmpty()) {
                    subTags.forEach { item ->
                        result.addElement(
                            LookupElementBuilder.create(item.name)
                                .withTypeText(item.description, true)
                                .withIcon(AntlersIcons.FILE)
                        )
                    }
                    return
                }
            }
        }

        // General completions: tags + variables
        if (settings.enableTagCompletion) {
            StatamicData.TAGS.forEach { item ->
                result.addElement(
                    LookupElementBuilder.create(item.name)
                        .withTypeText(item.description, true)
                        .withIcon(AntlersIcons.FILE)
                        .bold()
                )
            }
        }

        if (settings.enableVariableCompletion) {
            StatamicData.VARIABLES.forEach { item ->
                result.addElement(
                    LookupElementBuilder.create(item.name)
                        .withTypeText(item.description, true)
                )
            }
        }
    }

    private fun addPartialCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val project = parameters.position.project
        val scope = GlobalSearchScope.allScope(project)
        val seen = mutableSetOf<String>()

        for (ext in AntlersPartialPaths.extensions()) {
            val files = FilenameIndex.getAllFilesByExt(project, ext, scope)
            for (file in files) {
                val partialPath = AntlersPartialPaths.lookupPath(file) ?: continue
                if (seen.add(partialPath)) {
                    result.addElement(
                        LookupElementBuilder.create(partialPath)
                            .withTypeText(file.name, true)
                            .withIcon(AntlersIcons.FILE)
                    )
                }
            }
        }
    }
}
