package com.antlers.support.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.antlers.support.AntlersIcons
import com.antlers.support.psi.*

class AntlersStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? NavigatablePsiElement)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigateToSource() == true

    override fun getAlphaSortKey(): String = getPresentation().presentableText ?: ""

    override fun getPresentation(): ItemPresentation {
        val text = when (element) {
            is AntlersAntlersTag -> getTagPresentation()
            is AntlersConditionalTag -> getConditionalPresentation()
            is AntlersComment -> "{{# comment #}}"
            is AntlersNoparseBlock -> "{{ noparse }}"
            else -> element.containingFile?.name ?: "Antlers"
        }
        return PresentationData(text, null, AntlersIcons.FILE, null)
    }

    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        element.children.forEach { child ->
            when (child) {
                is AntlersAntlersTag,
                is AntlersConditionalTag,
                is AntlersComment,
                is AntlersNoparseBlock -> {
                    children.add(AntlersStructureViewElement(child))
                }
            }
        }
        return children.toTypedArray()
    }

    private fun getTagPresentation(): String {
        val tag = element as AntlersAntlersTag
        val tagExpr = PsiTreeUtil.findChildOfType(tag, AntlersTagExpression::class.java)
        val tagName = PsiTreeUtil.findChildOfType(tagExpr ?: tag, AntlersTagName::class.java)
        val closingTag = PsiTreeUtil.findChildOfType(tag, AntlersClosingTag::class.java)

        val name = tagName?.text ?: closingTag?.let {
            val innerName = PsiTreeUtil.findChildOfType(it, AntlersTagName::class.java)
            "/${innerName?.text ?: "..."}"
        } ?: "..."

        return "{{ $name }}"
    }

    private fun getConditionalPresentation(): String {
        val cond = element as AntlersConditionalTag
        val firstChild = cond.firstChild ?: return "{{ ... }}"
        val keyword = firstChild.text
        return "{{ $keyword }}"
    }
}
