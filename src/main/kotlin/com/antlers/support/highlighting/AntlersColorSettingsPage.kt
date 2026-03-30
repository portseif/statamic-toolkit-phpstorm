package com.antlers.support.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.antlers.support.AntlersIcons
import javax.swing.Icon

class AntlersColorSettingsPage : ColorSettingsPage {
    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Delimiters", AntlersHighlighterColors.DELIMITER),
            AttributesDescriptor("Keywords", AntlersHighlighterColors.KEYWORD),
            AttributesDescriptor("Tag Names", AntlersHighlighterColors.TAG_NAME),
            AttributesDescriptor("Parameters", AntlersHighlighterColors.PARAMETER),
            AttributesDescriptor("Identifiers", AntlersHighlighterColors.IDENTIFIER),
            AttributesDescriptor("Operators", AntlersHighlighterColors.OPERATOR),
            AttributesDescriptor("Pipe", AntlersHighlighterColors.PIPE),
            AttributesDescriptor("Strings", AntlersHighlighterColors.STRING),
            AttributesDescriptor("Numbers", AntlersHighlighterColors.NUMBER),
            AttributesDescriptor("Comments", AntlersHighlighterColors.COMMENT),
            AttributesDescriptor("Punctuation", AntlersHighlighterColors.PUNCTUATION),
            AttributesDescriptor("PHP Content", AntlersHighlighterColors.PHP_CONTENT),
            AttributesDescriptor("Bad Character", AntlersHighlighterColors.BAD_CHARACTER),
        )
    }

    override fun getIcon(): Icon = AntlersIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = AntlersSyntaxHighlighter()

    override fun getDemoText(): String = """
<html>
<body>
    {{# This is a comment #}}

    {{ if logged_in }}
        <h1>{{ title | upper }}</h1>
        <p>{{ content }}</p>
    {{ elseif show_guest }}
        <p>Welcome, guest!</p>
    {{ else }}
        <p>{{ fallback ?? "No content" }}</p>
    {{ /if }}

    {{ collection:blog limit="5" order="date" }}
        <article>
            <h2>{{ title }}</h2>
            <span>{{ date | format('Y-m-d') }}</span>
            {{ if featured == true }}
                <span>Featured</span>
            {{ /if }}
        </article>
    {{ /collection:blog }}

    {{ partial:components/hero title="Welcome" }}

    {{ items = [1, 2, 3] }}
    {{ total = 42 + 8 }}
    {{ result = score >= 50 ? "pass" : "fail" }}

    {{? $${'$'}page = request()->path(); ?}}
    {{$ route('home') $}}
</body>
</html>
""".trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Antlers"
}
