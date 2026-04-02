package com.antlers.support.editor

import com.antlers.support.AntlersLanguage
import com.antlers.support.injection.AntlersAlpineReferenceResolver
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersGotoDeclarationHandlerTest : BasePlatformTestCase() {

    fun testAlpineMethodResolvesThroughPsiReference() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div x-data="{
                goal: '',
                selectGoal(val) {
                    this.goal = val
                }
            }">
                <button @click="sel<caret>ectGoal('sales')">Select</button>
            </div>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.doHighlighting()

        val offset = myFixture.editor.caretModel.offset
        val reference = myFixture.file.viewProvider.findReferenceAt(offset)

        assertNotNull("Expected PSI reference for Alpine method call", reference)
        assertEquals("selectGoal", reference!!.resolve()!!.text)
    }

    fun testPartialPathResolvesThroughPsiReferenceWithoutLinkingTagOrBraces() {
        val partialFile = myFixture.addFileToProject(
            "resources/views/partials/components/_hero.antlers.html",
            "<section>{{ title }}</section>"
        )

        val source = """
            {{ partial:components/hero }}
            """.trimIndent()
        myFixture.configureByText(
            "demo.antlers.html",
            source
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val braceOffset = source.indexOf("{{") + 1
        val tagOffset = source.indexOf("partial") + 1
        val pathOffset = source.indexOf("components") + 1

        assertNull("Expected no top-level reference on opening braces", myFixture.file.viewProvider.findReferenceAt(braceOffset))
        assertNull("Expected no top-level reference on tag head", myFixture.file.viewProvider.findReferenceAt(tagOffset))

        val reference = myFixture.file.viewProvider.findReferenceAt(pathOffset)
        assertNotNull("Expected partial path PSI reference", reference)
        assertEquals("components/hero", reference!!.canonicalText)
        assertEquals(partialFile.virtualFile.path, reference.resolve()!!.containingFile.virtualFile.path)
    }

    fun testAlpineXForLoopVariablesResolveThroughPsiReference() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <template x-for="(bar, i) in heroBars">
                <div :class="heroHovered === i ? 'active' : ''" x-text="bar"></div>
            </template>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.doHighlighting()

        val barOffset = myFixture.file.text.indexOf("x-text=\"bar\"") + "x-text=\"".length + 1
        val barReference = injectedReferenceAt(barOffset)
        assertEquals("bar", (AntlersAlpineReferenceResolver.resolve(barReference) as PsiNamedElement).name)

        val indexOffset = myFixture.file.text.indexOf(" i ?") + 1
        val indexReference = injectedReferenceAt(indexOffset)
        assertEquals("i", (AntlersAlpineReferenceResolver.resolve(indexReference) as PsiNamedElement).name)
    }

    private fun injectedReferenceAt(offset: Int): JSReferenceExpression {
        val templateLanguage = myFixture.file.viewProvider.languages.first { it != myFixture.file.language }
        val templateDataFile = myFixture.file.viewProvider.getPsi(templateLanguage)
            ?: error("Expected template data PSI file for ${myFixture.file.name}")

        val injectedElement = InjectedLanguageManager.getInstance(project)
            .findInjectedElementAt(templateDataFile, offset)
            ?: error("Expected injected element at offset $offset")

        return PsiTreeUtil.getParentOfType(injectedElement, JSReferenceExpression::class.java, false)
            ?: error("Expected JS reference expression at offset $offset")
    }
}
