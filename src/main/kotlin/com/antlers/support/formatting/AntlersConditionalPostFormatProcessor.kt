package com.antlers.support.formatting

import com.antlers.support.AntlersBlockTags
import com.antlers.support.AntlersLanguage
import com.antlers.support.psi.AntlersAntlersTag
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor

class AntlersConditionalPostFormatProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
        val file = source.containingFile ?: return source
        if (!isAntlersFile(file)) return source

        processRange(file, file.textRange, settings)
        return source
    }

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        if (!isAntlersFile(source)) return rangeToReformat

        processRange(source, rangeToReformat, settings)
        return rangeToReformat
    }

    private fun processRange(file: PsiFile, range: TextRange, settings: CodeStyleSettings) {
        val project = file.project
        // Always use the Antlers PSI file for tag collection, even if we receive the HTML file
        val antlersFile = file.viewProvider.getPsi(AntlersLanguage.INSTANCE) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(antlersFile) ?: return
        val indentOptions = settings.getIndentOptionsByFile(file)
        val indentUnit = if (indentOptions.USE_TAB_CHARACTER) {
            "\t"
        } else {
            " ".repeat(indentOptions.INDENT_SIZE.coerceAtLeast(1))
        }

        val standaloneTagsByLine = collectStandaloneTagsByLine(antlersFile, document)
        if (standaloneTagsByLine.isEmpty()) return

        val startLine = document.getLineNumber(range.startOffset)
        val endLine = document.getLineNumber(range.endOffset)
        val lineStates = (0 until document.lineCount).map { line ->
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString()
            LineState(
                indent = lineText.takeWhile { it == ' ' || it == '\t' },
                trimmed = lineText.trim()
            )
        }
        val frames = ArrayDeque<StructureFrame>()
        val edits = mutableListOf<IndentEdit>()
        var insideInlineBlock = false

        for (line in 0 until document.lineCount) {
            val info = standaloneTagsByLine[line] ?: classifyHtmlLine(lineStates[line].trimmed)
            if (info == null) {
                continue
            }

            // Skip <script>/<style> block content entirely — the built-in
            // JS/CSS formatter handles indentation inside those blocks.
            if (info.kind == ControlTagKind.HTML_OPEN && info.htmlTagName in INLINE_CONTENT_TAGS) {
                updateFrames(info, frames)
                insideInlineBlock = true
                continue
            }
            if (insideInlineBlock) {
                if (info.kind == ControlTagKind.HTML_CLOSE && info.htmlTagName in INLINE_CONTENT_TAGS) {
                    insideInlineBlock = false
                    updateFrames(info, frames)
                }
                continue
            }

            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString()
            val currentIndent = lineText.takeWhile { it == ' ' || it == '\t' }

            val desiredIndent = indentUnit.repeat(desiredIndentLevelFor(info, frames).coerceAtLeast(0))
            if (line in startLine..endLine && desiredIndent != currentIndent) {
                edits.add(IndentEdit(lineStart, lineStart + currentIndent.length, desiredIndent))
            }

            updateFrames(info, frames)
        }

        for (edit in edits.asReversed()) {
            document.replaceString(edit.startOffset, edit.endOffset, edit.indent)
        }

        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private fun collectStandaloneTagsByLine(file: PsiFile, document: com.intellij.openapi.editor.Document): Map<Int, StructuralLine> {
        val result = mutableMapOf<Int, StructuralLine>()

        for (child in file.children) {
            val tag = child as? AntlersAntlersTag ?: continue
            val range = tag.textRange
            val startLine = document.getLineNumber(range.startOffset)
            val endLine = document.getLineNumber(range.endOffset)
            if (startLine != endLine) continue

            val lineStart = document.getLineStartOffset(startLine)
            val lineEnd = document.getLineEndOffset(startLine)
            val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString()
            if (lineText.trim() != tag.text.trim()) continue

            result[startLine] = classify(tag)
        }

        return result
    }

    private fun classify(tag: AntlersAntlersTag): StructuralLine {
        val conditional = tag.conditionalTag
        if (conditional != null) {
            return when {
                conditional.keywordIf != null -> StructuralLine(ControlTagKind.OPEN_IF)
                conditional.keywordUnless != null -> StructuralLine(ControlTagKind.OPEN_UNLESS)
                conditional.keywordSwitch != null -> StructuralLine(ControlTagKind.OPEN_SWITCH)
                conditional.keywordElseif != null -> StructuralLine(ControlTagKind.ELSEIF)
                conditional.keywordElse != null -> StructuralLine(ControlTagKind.ELSE)
                conditional.keywordEndif != null -> StructuralLine(ControlTagKind.CLOSE_IF)
                conditional.keywordEndunless != null -> StructuralLine(ControlTagKind.CLOSE_UNLESS)
                else -> StructuralLine(ControlTagKind.OTHER_TAG)
            }
        }

        val closingName = tag.closingTag?.tagName?.text
        if (closingName != null) {
            return when (closingName.substringBefore(':')) {
                "if" -> StructuralLine(ControlTagKind.CLOSE_IF)
                "unless" -> StructuralLine(ControlTagKind.CLOSE_UNLESS)
                "switch" -> StructuralLine(ControlTagKind.CLOSE_SWITCH)
                else -> StructuralLine(ControlTagKind.CLOSE_TAG_PAIR, htmlTagName = closingName.substringBefore(':'))
            }
        }

        // Check if this is a known block tag opener (e.g. {{ collection:blog }})
        val tagExpr = tag.tagExpression
        if (tagExpr != null) {
            val tagName = tagExpr.tagName?.text
            if (tagName != null) {
                val rootName = tagName.substringBefore(':')
                if (rootName in AntlersBlockTags.NAMES) {
                    return StructuralLine(ControlTagKind.OPEN_TAG_PAIR, htmlTagName = rootName)
                }
            }
        }

        return StructuralLine(ControlTagKind.OTHER_TAG)
    }

    private fun desiredIndentLevelFor(
        info: StructuralLine,
        frames: ArrayDeque<StructureFrame>
    ): Int {
        return when (info.kind) {
            ControlTagKind.OPEN_IF,
            ControlTagKind.OPEN_UNLESS,
            ControlTagKind.OPEN_SWITCH,
            ControlTagKind.OPEN_TAG_PAIR,
            ControlTagKind.CONTENT_LINE,
            ControlTagKind.OTHER_TAG -> frames.size

            ControlTagKind.ELSE,
            ControlTagKind.ELSEIF -> frames.lastMatchingIndex(StructureFrame::isBranchable) ?: frames.size

            ControlTagKind.CLOSE_IF -> frames.lastMatchingIndex { it.kind == StructureKind.IF } ?: frames.size
            ControlTagKind.CLOSE_UNLESS -> frames.lastMatchingIndex { it.kind == StructureKind.UNLESS } ?: frames.size
            ControlTagKind.CLOSE_SWITCH -> frames.lastMatchingIndex { it.kind == StructureKind.SWITCH } ?: frames.size

            ControlTagKind.CLOSE_TAG_PAIR -> {
                val expectedTagName = info.htmlTagName
                frames.lastMatchingIndex { it.kind == StructureKind.TAG_PAIR && it.tagName == expectedTagName }
                    ?: frames.lastMatchingIndex { it.kind == StructureKind.TAG_PAIR }
                    ?: frames.size
            }

            ControlTagKind.HTML_OPEN,
            ControlTagKind.HTML_SELF_CLOSING -> frames.size

            ControlTagKind.HTML_CLOSE -> {
                val expectedTagName = info.htmlTagName
                frames.lastMatchingIndex { it.kind == StructureKind.HTML && it.tagName == expectedTagName }
                    ?: frames.lastMatchingIndex { it.kind == StructureKind.HTML }
                    ?: frames.size
            }
        }
    }

    private fun updateFrames(
        info: StructuralLine,
        frames: ArrayDeque<StructureFrame>
    ) {
        when (info.kind) {
            ControlTagKind.OPEN_IF -> frames.addLast(StructureFrame(StructureKind.IF))
            ControlTagKind.OPEN_UNLESS -> frames.addLast(StructureFrame(StructureKind.UNLESS))
            ControlTagKind.OPEN_SWITCH -> frames.addLast(StructureFrame(StructureKind.SWITCH))
            ControlTagKind.CLOSE_IF -> frames.removeLastMatching { it.kind == StructureKind.IF }
            ControlTagKind.CLOSE_UNLESS -> frames.removeLastMatching { it.kind == StructureKind.UNLESS }
            ControlTagKind.CLOSE_SWITCH -> frames.removeLastMatching { it.kind == StructureKind.SWITCH }
            ControlTagKind.OPEN_TAG_PAIR -> frames.addLast(StructureFrame(StructureKind.TAG_PAIR, info.htmlTagName))
            ControlTagKind.CLOSE_TAG_PAIR -> {
                val expectedTagName = info.htmlTagName
                val removed = frames.removeLastMatching { it.kind == StructureKind.TAG_PAIR && it.tagName == expectedTagName }
                if (!removed) {
                    frames.removeLastMatching { it.kind == StructureKind.TAG_PAIR }
                }
            }
            ControlTagKind.HTML_OPEN -> frames.addLast(StructureFrame(StructureKind.HTML, info.htmlTagName))
            ControlTagKind.HTML_CLOSE -> {
                val expectedTagName = info.htmlTagName
                val removed = frames.removeLastMatching { it.kind == StructureKind.HTML && it.tagName == expectedTagName }
                if (!removed) {
                    frames.removeLastMatching { it.kind == StructureKind.HTML }
                }
            }
            ControlTagKind.HTML_SELF_CLOSING,
            ControlTagKind.CONTENT_LINE,
            ControlTagKind.ELSE,
            ControlTagKind.ELSEIF,
            ControlTagKind.OTHER_TAG -> Unit
        }
    }

    private fun isAntlersFile(file: PsiFile): Boolean {
        return file.viewProvider.baseLanguage == AntlersLanguage.INSTANCE
    }

    private data class IndentEdit(
        val startOffset: Int,
        val endOffset: Int,
        val indent: String
    )

    private data class StructureFrame(
        val kind: StructureKind,
        val tagName: String? = null
    ) {
        fun isBranchable(): Boolean = kind == StructureKind.IF || kind == StructureKind.UNLESS
    }

    private data class StructuralLine(
        val kind: ControlTagKind,
        val htmlTagName: String? = null
    )

    private data class LineState(
        val indent: String,
        val trimmed: String
    )

    private enum class StructureKind {
        HTML,
        IF,
        UNLESS,
        SWITCH,
        TAG_PAIR
    }

    private enum class ControlTagKind {
        HTML_OPEN,
        HTML_CLOSE,
        HTML_SELF_CLOSING,
        OPEN_IF,
        OPEN_UNLESS,
        OPEN_SWITCH,
        ELSEIF,
        ELSE,
        CLOSE_IF,
        CLOSE_UNLESS,
        CLOSE_SWITCH,
        OPEN_TAG_PAIR,
        CLOSE_TAG_PAIR,
        CONTENT_LINE,
        OTHER_TAG
    }

    companion object {
        private val htmlOpeningTagPattern = Regex("""<([A-Za-z][\w:-]*)(?:\s+[^<>]*)?>""")
        private val htmlClosingTagPattern = Regex("""</([A-Za-z][\w:-]*)\s*>""")
        private val htmlSelfClosingTagPattern = Regex("""<([A-Za-z][\w:-]*)(?:\s+[^<>]*)?/\s*>""")
        // Multi-line opening tag: starts with <tagName but doesn't close on this line
        private val htmlMultiLineOpenPattern = Regex("""<([A-Za-z][\w:-]*)(?:\s+.*)?""")
        private val INLINE_CONTENT_TAGS = setOf("script", "style")
        // HTML void elements: self-closing by spec, never have a </tag> closer.
        private val HTML_VOID_ELEMENTS = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr",
        )
    }

    private fun classifyHtmlLine(trimmed: String): StructuralLine? {
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("<!--")) return null
        if (trimmed.startsWith("<!")) return null
        if (trimmed.startsWith("<?")) return null

        htmlClosingTagPattern.matchEntire(trimmed)?.let { match ->
            return StructuralLine(ControlTagKind.HTML_CLOSE, match.groupValues[1].lowercase())
        }

        htmlSelfClosingTagPattern.matchEntire(trimmed)?.let { match ->
            return StructuralLine(ControlTagKind.HTML_SELF_CLOSING, match.groupValues[1].lowercase())
        }

        htmlOpeningTagPattern.matchEntire(trimmed)?.let { match ->
            val tagName = match.groupValues[1].lowercase()
            val kind = if (tagName in HTML_VOID_ELEMENTS) ControlTagKind.HTML_SELF_CLOSING else ControlTagKind.HTML_OPEN
            return StructuralLine(kind, tagName)
        }

        // Detect multi-line opening tags: <div\n  id="..."  class="..."\n>
        // The first line starts with <tagName but the > is on a later line.
        if (trimmed.startsWith("<") && !trimmed.startsWith("</")) {
            htmlMultiLineOpenPattern.matchEntire(trimmed)?.let { match ->
                val tagName = match.groupValues[1].lowercase()
                val kind = if (tagName in HTML_VOID_ELEMENTS) ControlTagKind.HTML_SELF_CLOSING else ControlTagKind.HTML_OPEN
                return StructuralLine(kind, tagName)
            }
        }

        return StructuralLine(ControlTagKind.CONTENT_LINE)
    }

    private fun ArrayDeque<StructureFrame>.lastMatchingIndex(predicate: (StructureFrame) -> Boolean): Int? {
        val items = this.toList()
        for (index in items.lastIndex downTo 0) {
            if (predicate(items[index])) {
                return index
            }
        }
        return null
    }

    private fun ArrayDeque<StructureFrame>.removeLastMatching(predicate: (StructureFrame) -> Boolean): Boolean {
        val items = toMutableList()
        val index = items.indexOfLast(predicate)
        if (index < 0) return false

        clear()
        items.removeAt(index)
        items.forEach(::addLast)
        return true
    }

}
