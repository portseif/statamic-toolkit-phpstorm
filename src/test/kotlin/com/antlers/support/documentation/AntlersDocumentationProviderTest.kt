package com.antlers.support.documentation

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersDocumentationProviderTest : BasePlatformTestCase() {

    fun testGenerateDocForOfficialTag() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ form:cre<caret>ate in="contact" }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val doc = documentationAtCaret()

        assertNotNull("Expected documentation for form:create", doc)
        assertTrue(doc!!.contains("Statamic Tag"))
        assertTrue(doc.contains("form:create"))
        assertTrue(doc.contains("collect, report, and reuse user submitted data"))
        assertTrue(doc.contains("https://statamic.dev/tags/form-create"))
        assertTrue(doc.contains("Example"))
    }

    fun testGenerateDocForTagHeadOfShorthandTag() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ na<caret>v:footer_product }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val doc = documentationAtCaret()

        assertNotNull("Expected documentation for nav tag head", doc)
        assertTrue(doc!!.contains("Statamic Tag"))
        assertTrue(doc.contains("nav"))
        assertTrue(doc.contains("https://statamic.dev/tags/nav"))
    }

    fun testGenerateDocForOfficialModifier() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ title | upp<caret>er }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val doc = documentationAtCaret()

        assertNotNull("Expected documentation for upper modifier", doc)
        assertTrue(doc!!.contains("Statamic Modifier"))
        assertTrue(doc.contains("upper"))
        assertTrue(doc.contains("uppercase"))
        assertTrue(doc.contains("https://statamic.dev/modifiers/upper"))
    }

    fun testGenerateDocForOfficialVariable() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <form>
                {{ csr<caret>f_token }}
            </form>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val doc = documentationAtCaret()

        assertNotNull("Expected documentation for csrf_token", doc)
        assertTrue(doc!!.contains("Statamic Variable"))
        assertTrue(doc.contains("csrf_token"))
        assertTrue(doc.contains("CSRF token"))
        assertTrue(doc.contains("https://statamic.dev/variables/csrf_token"))
    }

    private fun documentationAtCaret(): String? {
        val originalElement = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
            ?: error("Expected PSI element at caret")
        val manager = DocumentationManager.getInstance(project)
        val targetElement = manager.findTargetElement(myFixture.editor, myFixture.file, originalElement)
            ?: originalElement
        val provider = DocumentationManager.getProviderFromElement(targetElement)
        return provider.generateDoc(targetElement, originalElement)
    }
}
