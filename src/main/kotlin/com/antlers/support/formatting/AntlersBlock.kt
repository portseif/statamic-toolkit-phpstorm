package com.antlers.support.formatting

import com.intellij.formatting.*
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.antlers.support.AntlersLanguage
import com.antlers.support.psi.AntlersTypes
import com.antlers.support.lexer.AntlersTokenTypes

class AntlersBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    blockFactory: TemplateLanguageBlockFactory,
    codeStyleSettings: CodeStyleSettings,
    foreignChildren: List<DataLanguageBlockWrapper>?
) : TemplateLanguageBlock(node, wrap, alignment, blockFactory, codeStyleSettings, foreignChildren) {

    // Build spacing rules once per block using the active code style settings.
    // Rules are ordered from most-specific to least-specific; the SpacingBuilder
    // returns the first match.
    private val spacingBuilder: SpacingBuilder = SpacingBuilder(codeStyleSettings, AntlersLanguage.INSTANCE)
        // ── Delimiters ──────────────────────────────────────────────────────────
        // {{ tag }}  not  {{tag}}
        .after(AntlersTokenTypes.ANTLERS_OPEN).spaces(1)
        .before(AntlersTokenTypes.ANTLERS_CLOSE).spaces(1)
        // {{ tag /}}  not  {{ tag/}}
        .before(AntlersTokenTypes.TAG_SELF_CLOSE).spaces(1)

        // ── Comparison / equality operators ─────────────────────────────────────
        .around(AntlersTokenTypes.OP_IDENTICAL).spaces(1)      // ===
        .around(AntlersTokenTypes.OP_NOT_IDENTICAL).spaces(1)  // !==
        .around(AntlersTokenTypes.OP_EQUALS).spaces(1)         // ==
        .around(AntlersTokenTypes.OP_NOT_EQUALS).spaces(1)     // !=
        .around(AntlersTokenTypes.OP_SPACESHIP).spaces(1)      // <=>
        .around(AntlersTokenTypes.OP_GREATER_EQUAL).spaces(1)  // >=
        .around(AntlersTokenTypes.OP_LESS_EQUAL).spaces(1)     // <=
        .around(AntlersTokenTypes.OP_GREATER).spaces(1)        // >
        .around(AntlersTokenTypes.OP_LESS).spaces(1)           // <

        // ── Logical operators ────────────────────────────────────────────────────
        .around(AntlersTokenTypes.OP_AND).spaces(1)            // &&
        .around(AntlersTokenTypes.OP_OR).spaces(1)             // ||
        .around(AntlersTokenTypes.OP_NOT).spaces(1)            // !
        .around(AntlersTokenTypes.OP_NULL_COALESCE).spaces(1)  // ??
        // Word-form logical operators
        .around(AntlersTokenTypes.KEYWORD_AND).spaces(1)       // and
        .around(AntlersTokenTypes.KEYWORD_OR).spaces(1)        // or
        .around(AntlersTokenTypes.KEYWORD_XOR).spaces(1)       // xor
        .around(AntlersTokenTypes.KEYWORD_NOT).spaces(1)       // not

        // ── Arithmetic operators ─────────────────────────────────────────────────
        .around(AntlersTokenTypes.OP_PLUS).spaces(1)
        .around(AntlersTokenTypes.OP_MINUS).spaces(1)
        .around(AntlersTokenTypes.OP_MULTIPLY).spaces(1)
        // OP_DIVIDE gets .none() rather than .spaces(1) because in Antlers it doubles
        // as a path separator: partial:partials/sections/hero — spaces would break paths.
        // .none() actively removes any accidental spaces (e.g. from a previous bad run).
        .around(AntlersTokenTypes.OP_DIVIDE).none()
        .around(AntlersTokenTypes.OP_MODULO).spaces(1)
        .around(AntlersTokenTypes.OP_POWER).spaces(1)          // **
        .around(AntlersTokenTypes.OP_RANGE).spaces(1)          // ..

        // ── Ternary ──────────────────────────────────────────────────────────────
        .around(AntlersTokenTypes.OP_TERNARY_QUESTION).spaces(1) // ?
        .around(AntlersTokenTypes.OP_GATEKEEPER).spaces(1)       // ?=

        // ── Modifier pipe ────────────────────────────────────────────────────────
        // {{ title | upper | truncate:100 }}
        .around(AntlersTokenTypes.OP_PIPE).spaces(1)

        // ── Parameter assignment: no space — limit="5" not limit = "5" ──────────
        .around(AntlersTokenTypes.OP_ASSIGN).none()

        // ── Colon: no space — collection:blog, truncate:100 ─────────────────────
        .around(AntlersTokenTypes.COLON).none()

        // ── Comma in modifier args ────────────────────────────────────────────────
        .before(AntlersTokenTypes.COMMA).none()
        .after(AntlersTokenTypes.COMMA).spaces(1)

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        // Let our rules run first; fall back to the TemplateLanguageBlock
        // implementation which handles DataLanguageBlockWrapper pairs.
        return spacingBuilder.getSpacing(this, child1, child2)
            ?: super.getSpacing(child1, child2)
    }

    override fun getIndent(): Indent {
        return when (myNode.elementType) {
            // TAG_EXPRESSION and CLOSING_TAG are children of ANTLERS_TAG; giving them
            // a normal indent keeps tag content aligned inside {{ }}.
            AntlersTypes.TAG_EXPRESSION,
            AntlersTypes.CLOSING_TAG -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    override fun getTemplateTextElementType() = AntlersTokenTypes.TEMPLATE_TEXT
}
