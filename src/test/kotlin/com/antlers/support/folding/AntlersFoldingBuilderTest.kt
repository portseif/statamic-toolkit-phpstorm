package com.antlers.support.folding

import com.antlers.support.AntlersLanguage
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFoldingBuilderTest : BasePlatformTestCase() {

    fun testConditionalFoldPlaceholderShowsFullCondition() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ if collection=="posts" }}
            <h2>{{ title }}</h2>
            {{ /if }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val antlersFile = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
            ?: error("Expected Antlers PSI file")
        val descriptors = AntlersFoldingBuilder().buildFoldRegions(antlersFile, myFixture.editor.document, false)

        assertEquals(1, descriptors.size)
        assertEquals(
            "{{ if collection==\"posts\" }}...",
            AntlersFoldingBuilder().getPlaceholderText(descriptors.single().element)
        )
    }

    fun testTagPairFoldPlaceholderShowsOpeningTag() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ collection:posts }}
            {{ entry }}
            <h2>{{ title }}</h2>
            {{ /entry }}
            {{ /collection:posts }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val antlersFile = myFixture.file.viewProvider.getPsi(AntlersLanguage.INSTANCE)
            ?: error("Expected Antlers PSI file")
        val descriptors = AntlersFoldingBuilder().buildFoldRegions(antlersFile, myFixture.editor.document, false)
        val placeholders = descriptors.map { AntlersFoldingBuilder().getPlaceholderText(it.element) }

        assertContainsElements(
            placeholders,
            "{{ collection:posts }}...",
            "{{ entry }}..."
        )
    }
}
