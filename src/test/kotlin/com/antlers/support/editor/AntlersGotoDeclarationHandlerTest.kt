package com.antlers.support.editor

import com.antlers.support.injection.AntlersAlpineReferenceResolver
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersGotoDeclarationHandlerTest : BasePlatformTestCase() {

    fun testGotoDeclarationResolvesAlpineMethodCallToXDataDefinition() {
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
        val sourceElement = myFixture.file.findElementAt(offset)
            ?: error("Expected source element at offset $offset")
        val targets = AntlersGotoDeclarationHandler()
            .getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)

        assertNotNull("Expected goto declaration target for Alpine method call", targets)
        assertEquals(1, targets!!.size)
        assertEquals("selectGoal", targets.single().text)
    }

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

    fun testGotoDeclarationResolvesPartialToUnderscoredViewFile() {
        val partialFile = myFixture.addFileToProject(
            "resources/views/partials/components/_hero.antlers.html",
            "<section>{{ title }}</section>"
        )

        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ partial:components/he<caret>ro }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val offset = myFixture.editor.caretModel.offset
        val sourceElement = myFixture.file.findElementAt(offset)
            ?: error("Expected source element at offset $offset")
        val targets = AntlersGotoDeclarationHandler()
            .getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)

        assertNotNull("Expected goto declaration target for Antlers partial", targets)
        assertEquals(1, targets!!.size)
        assertEquals(partialFile.virtualFile.path, targets.single().containingFile.virtualFile.path)
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
