package com.antlers.support.completion

import com.antlers.support.AntlersIcons
import com.antlers.support.statamic.StatamicCatalog
import com.antlers.support.statamic.StatamicScopeVariables
import com.antlers.support.statamic.StatamicTagParameters
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder

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
        val generated = StatamicCatalog.variables.map { CompletionItem(it.name, it.description) }
        val entryFields = StatamicScopeVariables.ENTRY_FIELDS.map {
            CompletionItem(it.name, it.description)
        }
        // Generated variables win on name collisions (e.g. "collection" is both an entry
        // field and a built-in variable — prefer the built-in doc).
        (generated + entryFields).distinctBy { it.name }
    }

    // -------------------------------------------------------------------------
    // Pre-built LookupElement lists.
    // LookupElementBuilder produces stateless, immutable elements — safe to
    // cache and reuse across completion sessions instead of rebuilding on every
    // invocation (which used to allocate N objects per keystroke).
    // -------------------------------------------------------------------------

    /** Ready-to-add elements for all top-level Statamic tags. */
    val TAG_ELEMENTS: List<LookupElement> by lazy {
        TAGS.map { item ->
            LookupElementBuilder.create(item.name)
                .withTypeText(item.description, true)
                .withIcon(AntlersIcons.FILE)
                .bold()
        }
    }

    /** Ready-to-add elements for all Statamic modifiers. */
    val MODIFIER_ELEMENTS: List<LookupElement> by lazy {
        MODIFIERS.map { item ->
            LookupElementBuilder.create(item.name)
                .withTypeText(item.description, true)
                .withIcon(AntlersIcons.FILE)
                .bold()
        }
    }

    /** Ready-to-add elements for all Statamic variables. */
    val VARIABLE_ELEMENTS: List<LookupElement> by lazy {
        VARIABLES.map { item ->
            LookupElementBuilder.create(item.name)
                .withTypeText(item.description, true)
        }
    }

    /** Pre-built sub-tag elements keyed by the parent tag name (e.g. "nav"). */
    private val SUB_TAG_ELEMENT_MAP: Map<String, List<LookupElement>> by lazy {
        TAG_SCOPES.mapValues { (_, items) ->
            items.map { item ->
                LookupElementBuilder.create(item.name)
                    .withTypeText(item.description, true)
                    .withIcon(AntlersIcons.FILE)
            }
        }
    }

    fun getSubTagElements(tagName: String): List<LookupElement> =
        SUB_TAG_ELEMENT_MAP[tagName].orEmpty()

    /** Pre-built parameter elements keyed by root tag name. */
    private val PARAMETER_ELEMENT_MAP: Map<String, List<LookupElement>> by lazy {
        val result = mutableMapOf<String, List<LookupElement>>()
        for ((tagName, params) in StatamicTagParameters.allEntries()) {
            result[tagName] = params.map { param ->
                LookupElementBuilder.create(param.name)
                    .withTypeText(param.description, true)
                    .withIcon(AntlersIcons.FILE)
                    .withInsertHandler { context: InsertionContext, _ ->
                        val editor = context.editor
                        val document = editor.document
                        val offset = context.tailOffset
                        document.insertString(offset, "=\"\"")
                        editor.caretModel.moveToOffset(offset + 2)
                    }
                    .let { if (param.required) it.bold() else it }
            }
        }
        result
    }

    fun getParameterElements(tagName: String): List<LookupElement> {
        val rootName = tagName.substringBefore(':')
        return PARAMETER_ELEMENT_MAP[rootName].orEmpty()
    }
}
