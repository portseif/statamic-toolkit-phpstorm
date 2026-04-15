package com.antlers.support.statamic

data class StatamicDocItem(
    val name: String,
    val displayName: String,
    val description: String,
    val example: String?,
    val url: String
)

object StatamicCatalog {
    val tags: List<StatamicDocItem> = GENERATED_TAGS
    val modifiers: List<StatamicDocItem> = GENERATED_MODIFIERS
    val variables: List<StatamicDocItem> = GENERATED_VARIABLES

    private val tagsByName = tags.associateBy { it.name }
    private val modifiersByName = modifiers.associateBy { it.name }
    private val variablesByName = variables.associateBy { it.name }
    private val entryFieldsByName: Map<String, StatamicDocItem> =
        StatamicScopeVariables.ENTRY_FIELDS.associate { field ->
            field.name to StatamicDocItem(
                name = field.name,
                displayName = field.name,
                description = field.description,
                example = null,
                url = "https://statamic.dev/variables",
            )
        }
    private val subTagsByRoot = tags
        .filter { ':' in it.name }
        .groupBy(
            keySelector = { it.name.substringBefore(':') },
            valueTransform = { it.copy(name = it.name.substringAfter(':')) }
        )

    fun findTag(name: String): StatamicDocItem? = tagsByName[name]

    fun resolveTag(name: String): StatamicDocItem? {
        return tagsByName[name] ?: tagsByName[name.substringBefore(':')]
    }

    fun isKnownTag(name: String): Boolean = resolveTag(name) != null

    fun findModifier(name: String): StatamicDocItem? = modifiersByName[name]

    /**
     * Look up a variable by name. Falls back to common entry fields (title, content, id, etc.)
     * if no generated variable matches, since Statamic templates are rendered in an entry
     * context where these fields are always available at the top level.
     */
    fun findVariable(name: String): StatamicDocItem? =
        variablesByName[name] ?: entryFieldsByName[name]

    fun subTags(rootName: String): List<StatamicDocItem> = subTagsByRoot[rootName].orEmpty()
}
