package com.antlers.support.editor

import com.intellij.lang.Commenter

class AntlersCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null

    override fun getBlockCommentPrefix(): String = "{{# "

    override fun getBlockCommentSuffix(): String = " #}}"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
