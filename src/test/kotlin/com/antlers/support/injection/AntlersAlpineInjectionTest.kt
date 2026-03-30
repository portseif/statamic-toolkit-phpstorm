package com.antlers.support.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersAlpineInjectionTest : BasePlatformTestCase() {

    fun testAlpineXDataInjectsJavascriptInAntlersFile() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div x-data="{ step: 1, next() { this.step++ } }"></div>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.doHighlighting()

        val offset = myFixture.file.text.indexOf("this.step") + 1
        val templateDataFile = templateDataPsiFile()
        val injected = InjectedLanguageManager.getInstance(project)
            .findInjectedElementAt(templateDataFile, offset)

        assertNotNull("Expected JavaScript to be injected into Antlers x-data", injected)
        assertEquals("JavaScript", injected!!.containingFile.language.id)
    }

    fun testAlpineEventShorthandInjectsJavascriptInAntlersFile() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <button @click="count++"></button>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.doHighlighting()

        val offset = myFixture.file.text.indexOf("count++") + 1
        val templateDataFile = templateDataPsiFile()
        val injected = InjectedLanguageManager.getInstance(project)
            .findInjectedElementAt(templateDataFile, offset)

        assertNotNull("Expected JavaScript to be injected into Antlers @click", injected)
        assertEquals("JavaScript", injected!!.containingFile.language.id)
    }

    fun testAlpineXForDoesNotInjectInvalidJavascript() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <template x-for="(bar, i) in heroBars"></template>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.doHighlighting()

        val offset = myFixture.file.text.indexOf("heroBars") + 1
        val templateDataFile = templateDataPsiFile()
        val injected = InjectedLanguageManager.getInstance(project)
            .findInjectedElementAt(templateDataFile, offset)

        assertNull("Did not expect JavaScript injection for Alpine x-for syntax", injected)
    }

    private fun templateDataPsiFile(): PsiFile {
        val baseFile = myFixture.file
        val templateLanguage = baseFile.viewProvider.languages.first { it != baseFile.language }
        return baseFile.viewProvider.getPsi(templateLanguage)
            ?: error("Expected template data PSI file for ${baseFile.name}")
    }
}
