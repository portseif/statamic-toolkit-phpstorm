package com.antlers.support.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.antlers.support.psi.AntlersAntlersTag
import com.antlers.support.psi.AntlersComment
import com.antlers.support.psi.AntlersConditionalTag
import com.antlers.support.psi.AntlersNoparseBlock

class AntlersStructureViewModel(psiFile: PsiFile, editor: Editor?) :
    StructureViewModelBase(psiFile, editor, AntlersStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = false

    override fun getSuitableClasses(): Array<Class<*>> = arrayOf(
        AntlersAntlersTag::class.java,
        AntlersComment::class.java,
        AntlersConditionalTag::class.java,
        AntlersNoparseBlock::class.java
    )
}
