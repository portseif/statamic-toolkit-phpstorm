package com.antlers.support.partials

import com.antlers.support.AntlersLanguage
import com.antlers.support.lexer.AntlersTokenTypes
import com.antlers.support.psi.AntlersTagName
import com.intellij.patterns.PlatformPatterns
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext

class AntlersPartialReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement()
                .withParent(AntlersTagName::class.java),
            AntlersPartialReferenceProvider()
        )
    }
}

private class AntlersPartialReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val reference = AntlersPartialReferenceSupport.referenceForElement(element)
        return if (reference != null) arrayOf(reference) else PsiReference.EMPTY_ARRAY
    }
}

internal object AntlersPartialReferenceSupport {
    fun referenceForElement(element: PsiElement): PsiReference? {
        val tagName = element.parent as? AntlersTagName ?: return null
        val prefix = partialPrefix(tagName.text) ?: return null

        val elementType = element.node?.elementType ?: return null
        if (elementType != AntlersTokenTypes.IDENTIFIER && elementType != AntlersTokenTypes.OP_DIVIDE) {
            return null
        }

        val relativeStart = element.textRange.startOffset - tagName.textRange.startOffset
        if (relativeStart < prefix.length) return null

        return AntlersPartialPsiReference(
            element = element,
            rangeInElement = TextRange(0, element.textLength),
            partialPath = tagName.text.removePrefix(prefix)
        )
    }

    private fun partialPrefix(text: String): String? {
        return when {
            text.startsWith("partial:") -> "partial:"
            text.startsWith("partial/") -> "partial/"
            else -> null
        }
    }
}

internal class AntlersPartialPsiReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val partialPath: String
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement, false) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (partialPath.isBlank()) return ResolveResult.EMPTY_ARRAY

        return AntlersPartialPaths.matchingPsiFiles(element.project, partialPath)
            .map(::PsiElementResolveResult)
            .toTypedArray()
    }

    override fun resolve(): PsiElement? = multiResolve(false).singleOrNull()?.element

    override fun getCanonicalText(): String = partialPath

    override fun getVariants(): Array<Any> = emptyArray()
}
