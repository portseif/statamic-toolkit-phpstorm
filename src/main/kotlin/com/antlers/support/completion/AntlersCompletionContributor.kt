package com.antlers.support.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.antlers.support.statamic.StatamicProjectCollections
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.antlers.support.AntlersIcons
import com.antlers.support.AntlersLanguage
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.partials.AntlersPartialPaths
import com.antlers.support.psi.AntlersAntlersTag
import com.antlers.support.psi.AntlersClosingTag
import com.antlers.support.psi.AntlersTagExpression
import com.antlers.support.psi.AntlersTagName
import com.antlers.support.settings.AntlersSettings
import com.antlers.support.statamic.StatamicScopeVariables

class AntlersCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(AntlersLanguage.INSTANCE),
            AntlersCompletionProvider()
        )
    }

    override fun invokeAutoPopup(position: com.intellij.psi.PsiElement, typeChar: Char): Boolean {
        // Auto-show completion after : (for partial: paths and sub-tags like nav:)
        if (typeChar == ':') return true
        return false
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

        // After | pipe — suggest modifiers (pre-built, no allocations)
        if (prevType == AntlersTokenTypes.OP_PIPE) {
            if (!settings.enableModifierCompletion) return
            result.addAllElements(StatamicData.MODIFIER_ELEMENTS)
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

            // After collection: — suggest collection handles from content/collections/
            if (tagName == "collection") {
                addCollectionCompletions(parameters, result)
                return
            }

            // After tag: — suggest sub-tags (pre-built per namespace)
            if (tagName != null && settings.enableTagCompletion) {
                val subTagElements = StatamicData.getSubTagElements(tagName)
                if (subTagElements.isNotEmpty()) {
                    result.addAllElements(subTagElements)
                    return
                }
            }
        }

        // Inside a tag expression — suggest parameters for that tag
        if (settings.enableParameterCompletion) {
            val tagExpr = PsiTreeUtil.getParentOfType(position, AntlersTagExpression::class.java)
            if (tagExpr != null) {
                val tagName = tagExpr.tagName?.text
                if (tagName != null) {
                    val allParams = StatamicData.getParameterElements(tagName)
                    if (allParams.isNotEmpty()) {
                        // Filter out already-used parameter names
                        val usedNames = tagExpr.parameterList.mapNotNull { param ->
                            param.children.firstOrNull { child ->
                                val t = child.node?.elementType
                                t == AntlersTokenTypes.IDENTIFIER ||
                                    t == AntlersTokenTypes.KEYWORD_AS ||
                                    t == AntlersTokenTypes.KEYWORD_WHERE ||
                                    t == AntlersTokenTypes.KEYWORD_ORDERBY
                            }?.text
                        }.toSet()
                        val filtered = allParams.filter { it.lookupString !in usedNames }
                        result.addAllElements(filtered)
                    }
                }
            }
        }

        // Scope-aware variables: suggest loop vars + entry fields inside tag pair blocks
        if (settings.enableVariableCompletion) {
            val enclosingTag = findEnclosingTagPair(parameters)
            if (enclosingTag != null) {
                val scopeVars = StatamicScopeVariables.forTag(
                    project = position.project,
                    tagName = enclosingTag.name,
                    tagText = enclosingTag.text
                )
                for (v in scopeVars) {
                    result.addElement(
                        LookupElementBuilder.create(v.name)
                            .withTypeText(v.description, true)
                            .withTailText("  ${v.type}", true)
                            .withIcon(AntlersIcons.FILE)
                            .bold()
                    )
                }
            }
        }

        // General completions: tags + variables (pre-built, no allocations)
        if (settings.enableTagCompletion) {
            result.addAllElements(StatamicData.TAG_ELEMENTS)
        }

        if (settings.enableVariableCompletion) {
            result.addAllElements(StatamicData.VARIABLE_ELEMENTS)
        }
    }

    /**
     * Walks backward through the Antlers PSI tree to find the nearest
     * enclosing tag pair (e.g., collection, nav, taxonomy) that the caret
     * is inside. Uses a stack to match opening/closing tags.
     */
    private fun findEnclosingTagPair(parameters: CompletionParameters): EnclosingTagScope? {
        val position = parameters.position
        val antlersFile = position.containingFile?.viewProvider
            ?.getPsi(AntlersLanguage.INSTANCE) ?: return null
        val caretOffset = parameters.offset

        // Walk all top-level children and use a stack to track open tag pairs
        val openTags = mutableListOf<EnclosingTagScope>()

        for (child in antlersFile.children) {
            if (child.textRange.startOffset >= caretOffset) break

            if (child is AntlersAntlersTag) {
                val tagExpr = PsiTreeUtil.findChildOfType(child, AntlersTagExpression::class.java)
                val tagName = PsiTreeUtil.findChildOfType(tagExpr ?: child, AntlersTagName::class.java)
                val closingTag = PsiTreeUtil.findChildOfType(child, AntlersClosingTag::class.java)

                if (closingTag != null) {
                    // This is a closing tag like {{ /collection }}
                    val closeName = PsiTreeUtil.findChildOfType(closingTag, AntlersTagName::class.java)?.text
                    if (closeName != null) {
                        // Pop matching tag from stack
                        val closeRoot = closeName.substringBefore(':')
                        val idx = openTags.indexOfLast { it.name.substringBefore(':') == closeRoot }
                        if (idx >= 0) openTags.removeAt(idx)
                    }
                } else if (tagName != null) {
                    val name = tagName.text
                    if (name != null && StatamicScopeVariables.hasScopeVariables(name)) {
                        openTags.add(EnclosingTagScope(name = name, text = tagExpr?.text ?: child.text))
                    }
                }
            }
        }

        // The innermost open tag is the enclosing one
        return openTags.lastOrNull()
    }

    private fun addCollectionCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val project = parameters.position.project
        val service = StatamicProjectCollections.getInstance(project)
        service.ensureLoaded()

        for (handle in service.handles) {
            result.addElement(
                LookupElementBuilder.create(handle)
                    .withTypeText("Collection", true)
                    .withIcon(AntlersIcons.FILE)
            )
        }
    }

    private fun addPartialCompletions(parameters: CompletionParameters, result: CompletionResultSet) {
        val project = parameters.position.project
        val scope = AntlersPartialPaths.searchScope(project)
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

private data class EnclosingTagScope(
    val name: String,
    val text: String
)
