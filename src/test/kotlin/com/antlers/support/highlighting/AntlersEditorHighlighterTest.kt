package com.antlers.support.highlighting

import com.antlers.support.lexer.AntlersTokenTypes
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersEditorHighlighterTest : BasePlatformTestCase() {

    fun testEditorHighlighterIncludesAntlersTokens() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div>
                {{ if logged_in }}
                    {{ partial:components/hero }}
                {{ /if }}
            </div>
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val highlighter = myFixture.editor.highlighter
        highlighter.setText(myFixture.file.text)
        val iterator = highlighter.createIterator(0)

        val seenTokenTypes = mutableListOf<String>()
        while (!iterator.atEnd()) {
            seenTokenTypes += iterator.tokenType.toString()
            iterator.advance()
        }

        assertContainsElements(
            seenTokenTypes,
            AntlersTokenTypes.ANTLERS_OPEN.toString(),
            AntlersTokenTypes.KEYWORD_IF.toString(),
            AntlersTokenTypes.ANTLERS_CLOSE.toString()
        )
    }

    fun testSemanticHighlightingColorsPartialTagAndParameters() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ partial:components/hero title="Welcome" }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text

        assertTrue(
            "Expected partial tag name to use Antlers tag highlighting",
            infos.any {
                it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_NAME &&
                    text.substring(it.startOffset, it.endOffset) == "partial"
            }
        )

        assertTrue(
            "Expected parameter names to use Antlers parameter highlighting",
            infos.any {
                it.forcedTextAttributesKey == AntlersHighlighterColors.PARAMETER &&
                    text.substring(it.startOffset, it.endOffset) == "title"
            }
        )
    }

    fun testSemanticHighlightingUnderlinesOnlyPartialPathPortion() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ partial:partials/sections/footer }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text
        val pathHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_PATH }
            .map { text.substring(it.startOffset, it.endOffset) }
        val tagHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_NAME }
            .map { text.substring(it.startOffset, it.endOffset) }
        val delimiterHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.DELIMITER }
            .map { text.substring(it.startOffset, it.endOffset) }

        assertContainsElements(pathHighlights, "partials", "sections", "footer", "/")
        assertDoesntContain(pathHighlights, "partial")
        assertDoesntContain(pathHighlights, ":")
        assertContainsElements(tagHighlights, "partial")
        assertDoesntContain(tagHighlights, ":")
        assertContainsElements(delimiterHighlights, ":")
    }

    fun testSemanticHighlightingOnlyColorsTagHeadForNamespacedTag() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ nav:footer_product }}
            <a href="{{ url }}">{{ title }}</a>
            {{ /nav:footer_product }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text
        val tagNameHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_NAME }
            .map { text.substring(it.startOffset, it.endOffset) }

        assertContainsElements(tagNameHighlights, "nav", "footer_product")
        assertDoesntContain(tagNameHighlights, "url")
        assertDoesntContain(tagNameHighlights, "title")
    }

    fun testSemanticHighlightingTreatsEntryBlockTagsAsRealTags() {
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

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text
        val tagNameHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_NAME }
            .map { text.substring(it.startOffset, it.endOffset) }

        assertContainsElements(tagNameHighlights, "collection", "posts", "entry")
        assertTrue(tagNameHighlights.count { it == "entry" } >= 2)
        assertDoesntContain(tagNameHighlights, "title")
    }
}
