package com.antlers.support.injection

import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext

class AntlersAlpineReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: com.intellij.psi.PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JSReferenceExpression::class.java),
            AntlersAlpineReferenceProvider()
        )
    }
}

private class AntlersAlpineReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val referenceExpression = element as? JSReferenceExpression ?: return PsiReference.EMPTY_ARRAY
        val nameElement = referenceExpression.referenceNameElement ?: return PsiReference.EMPTY_ARRAY
        if (AntlersAlpineReferenceResolver.resolve(referenceExpression) == null) {
            return PsiReference.EMPTY_ARRAY
        }

        val rangeInElement = TextRange(
            nameElement.textRange.startOffset - referenceExpression.textRange.startOffset,
            nameElement.textRange.endOffset - referenceExpression.textRange.startOffset
        )

        return arrayOf(AntlersAlpinePsiReference(referenceExpression, rangeInElement))
    }
}

private class AntlersAlpinePsiReference(
    element: JSReferenceExpression,
    rangeInElement: TextRange
) : PsiReferenceBase<JSReferenceExpression>(element, rangeInElement, false) {

    override fun resolve(): PsiElement? {
        return AntlersAlpineReferenceResolver.resolve(element)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
