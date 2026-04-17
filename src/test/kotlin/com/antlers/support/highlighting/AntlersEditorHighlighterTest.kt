package com.antlers.support.highlighting

import com.antlers.support.lexer.AntlersTokenTypes
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.TextAttributesKey
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

    fun testSemanticHighlightingAppliesTagPathOnlyToPartialPathPortion() {
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

        assertContainsElements(pathHighlights, "partials", "sections", "footer", "/", "/")
        assertDoesntContain(pathHighlights, "partial")
        assertContainsElements(tagHighlights, "partial")
        assertDoesntContain(tagHighlights, "partials")
        assertDoesntContain(tagHighlights, "sections")
        assertDoesntContain(tagHighlights, "footer")
        assertContainsElements(delimiterHighlights, ":")

        val colonOffset = text.indexOf(':')
        val partialOffset = text.indexOf("partial") + 1
        val pathOffset = text.indexOf("partials") + 1
        assertContainsElements(highlightKeysAt(infos, partialOffset), AntlersHighlighterColors.TAG_NAME)
        assertDoesntContain(highlightKeysAt(infos, partialOffset), AntlersHighlighterColors.TAG_PATH)
        assertContainsElements(highlightKeysAt(infos, colonOffset), AntlersHighlighterColors.DELIMITER)
        assertDoesntContain(highlightKeysAt(infos, colonOffset), AntlersHighlighterColors.TAG_NAME)
        assertDoesntContain(highlightKeysAt(infos, colonOffset), AntlersHighlighterColors.TAG_PATH)
        assertContainsElements(highlightKeysAt(infos, pathOffset), AntlersHighlighterColors.TAG_PATH)
        assertDoesntContain(highlightKeysAt(infos, pathOffset), AntlersHighlighterColors.TAG_NAME)
    }

    fun testSemanticHighlightingKeepsPartialHeadAndBracesOffTagPath() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ partial:header }}
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

        assertContainsElements(pathHighlights, "header")
        assertDoesntContain(pathHighlights, "partial")
        assertContainsElements(tagHighlights, "partial")
        assertDoesntContain(tagHighlights, "header")

        val partialOffset = text.indexOf("partial") + 1
        val pathOffset = text.indexOf("header") + 1
        assertContainsElements(highlightKeysAt(infos, partialOffset), AntlersHighlighterColors.TAG_NAME)
        assertDoesntContain(highlightKeysAt(infos, partialOffset), AntlersHighlighterColors.TAG_PATH)
        assertContainsElements(highlightKeysAt(infos, pathOffset), AntlersHighlighterColors.TAG_PATH)
        assertDoesntContain(highlightKeysAt(infos, pathOffset), AntlersHighlighterColors.TAG_NAME)

        val closeBraceOffset = text.lastIndexOf("}}")
        assertDoesntContain(highlightKeysAt(infos, closeBraceOffset), AntlersHighlighterColors.TAG_PATH)
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

    fun testSemanticHighlightingDoesNotTreatNamespacedVariableExpressionAsTag() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ global:site_settings:hero_tagline ?? "Craftsmanship" }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text
        val delimiterHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.DELIMITER }
            .map { text.substring(it.startOffset, it.endOffset) }
        val tagNameHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_NAME }
            .map { text.substring(it.startOffset, it.endOffset) }

        assertEquals(2, delimiterHighlights.count { it == ":" })
        assertDoesntContain(tagNameHighlights, "global")
        assertDoesntContain(tagNameHighlights, "site_settings")
        assertDoesntContain(tagNameHighlights, "hero_tagline")

        for (offset in colonOffsets(text)) {
            assertContainsElements(highlightKeysAt(infos, offset), AntlersHighlighterColors.DELIMITER)
            assertDoesntContain(highlightKeysAt(infos, offset), AntlersHighlighterColors.TAG_NAME)
            assertDoesntContain(highlightKeysAt(infos, offset), AntlersHighlighterColors.TAG_PATH)
        }
    }

    fun testSemanticHighlightingDoesNotTreatConfigVariableExpressionAsTag() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ config:statamic:cp:enabled }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text
        val delimiterHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.DELIMITER }
            .map { text.substring(it.startOffset, it.endOffset) }
        val tagNameHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.TAG_NAME }
            .map { text.substring(it.startOffset, it.endOffset) }

        assertEquals(3, delimiterHighlights.count { it == ":" })
        assertDoesntContain(tagNameHighlights, "config")
        assertDoesntContain(tagNameHighlights, "statamic")
        assertDoesntContain(tagNameHighlights, "cp")
        assertDoesntContain(tagNameHighlights, "enabled")

        for (offset in colonOffsets(text)) {
            assertContainsElements(highlightKeysAt(infos, offset), AntlersHighlighterColors.DELIMITER)
            assertDoesntContain(highlightKeysAt(infos, offset), AntlersHighlighterColors.TAG_NAME)
            assertDoesntContain(highlightKeysAt(infos, offset), AntlersHighlighterColors.TAG_PATH)
        }
    }

    fun testSemanticHighlightingColorsAssignedNamespacedReferenceDelimiters() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ hero_heading = global:site_settings:hero_heading ?? "Expert Craftsmanship" }}
            """.trimIndent()
        )
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val infos = myFixture.doHighlighting()
        val text = myFixture.file.text
        val delimiterHighlights = infos
            .filter { it.forcedTextAttributesKey == AntlersHighlighterColors.DELIMITER }
            .map { text.substring(it.startOffset, it.endOffset) }

        assertEquals(2, delimiterHighlights.count { it == ":" })
    }

    private fun highlightKeysAt(infos: List<HighlightInfo>, offset: Int): List<TextAttributesKey> {
        return infos.asSequence()
            .filter { it.startOffset <= offset && offset < it.endOffset }
            .mapNotNull { it.forcedTextAttributesKey }
            .toList()
    }

    private fun colonOffsets(text: String): List<Int> {
        return text.mapIndexedNotNull { index, ch -> index.takeIf { ch == ':' } }
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
