package com.antlers.support.completion

import com.antlers.support.statamic.StatamicCatalog

object StatamicData {
    data class CompletionItem(val name: String, val description: String)

    val TAGS: List<CompletionItem> by lazy {
        StatamicCatalog.tags.map { CompletionItem(it.name, it.description) }
    }

    val MODIFIERS: List<CompletionItem> by lazy {
        StatamicCatalog.modifiers.map { CompletionItem(it.name, it.description) }
    }

    private val TAG_SCOPES: Map<String, List<CompletionItem>> by lazy {
        StatamicCatalog.tags
            .filter { ':' in it.name }
            .groupBy(
                keySelector = { it.name.substringBefore(':') },
                valueTransform = {
                    CompletionItem(
                        name = it.name.substringAfter(':'),
                        description = it.description
                    )
                }
            )
    }

    val VARIABLES: List<CompletionItem> by lazy {
        StatamicCatalog.variables.map { CompletionItem(it.name, it.description) }
    }

    fun getSubTags(tagName: String): List<CompletionItem> {
        return TAG_SCOPES[tagName].orEmpty()
    }
}
