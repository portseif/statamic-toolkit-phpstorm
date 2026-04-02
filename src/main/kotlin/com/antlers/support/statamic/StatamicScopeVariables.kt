package com.antlers.support.statamic

import com.intellij.openapi.project.Project

/**
 * Variables available inside tag pair loops.
 * These are injected into the completion context when the caret
 * is between an opening and closing tag pair.
 */
object StatamicScopeVariables {

    data class ScopeVariable(
        val name: String,
        val description: String,
        val type: String = "mixed"
    )

    /** Loop variables available inside all tag pair contexts. */
    val LOOP_VARIABLES: List<ScopeVariable> = listOf(
        ScopeVariable("first", "True if this is the first item", "boolean"),
        ScopeVariable("last", "True if this is the last item", "boolean"),
        ScopeVariable("count", "Current iteration (1-indexed)", "integer"),
        ScopeVariable("index", "Current iteration (0-indexed)", "integer"),
        ScopeVariable("order", "Alias for count", "integer"),
        ScopeVariable("total_results", "Total number of items in the loop", "integer"),
        ScopeVariable("no_results", "True when loop has no items", "boolean"),
    )

    /** Common entry fields available inside collection loops. */
    private val ENTRY_FIELDS: List<ScopeVariable> = listOf(
        ScopeVariable("title", "Entry title", "string"),
        ScopeVariable("slug", "Entry URL slug", "string"),
        ScopeVariable("url", "Entry URL path", "string"),
        ScopeVariable("permalink", "Full absolute URL", "string"),
        ScopeVariable("id", "Entry ID", "string"),
        ScopeVariable("date", "Entry date", "date"),
        ScopeVariable("content", "Entry content (markdown/HTML)", "string"),
        ScopeVariable("excerpt", "Entry excerpt", "string"),
        ScopeVariable("published", "Whether the entry is published", "boolean"),
        ScopeVariable("status", "Entry status (published/draft)", "string"),
        ScopeVariable("collection", "Collection handle the entry belongs to", "string"),
        ScopeVariable("edit_url", "Control Panel edit URL", "string"),
        ScopeVariable("api_url", "Content API URL", "string"),
        ScopeVariable("locale", "Entry locale", "string"),
        ScopeVariable("last_modified", "Last modified timestamp", "date"),
        ScopeVariable("updated_at", "Updated at timestamp", "date"),
        ScopeVariable("updated_by", "User who last updated", "object"),
    )

    /** Nav-specific fields available inside nav loops. */
    private val NAV_FIELDS: List<ScopeVariable> = listOf(
        ScopeVariable("title", "Nav item title", "string"),
        ScopeVariable("url", "Nav item URL", "string"),
        ScopeVariable("permalink", "Full absolute URL", "string"),
        ScopeVariable("is_current", "True if this is the current page", "boolean"),
        ScopeVariable("is_parent", "True if this is a parent of the current page", "boolean"),
        ScopeVariable("is_external", "True if this links externally", "boolean"),
        ScopeVariable("children", "Child nav items", "array"),
        ScopeVariable("depth", "Nesting depth (1-based)", "integer"),
    )

    /** Taxonomy-specific fields. */
    private val TAXONOMY_FIELDS: List<ScopeVariable> = listOf(
        ScopeVariable("title", "Term title", "string"),
        ScopeVariable("slug", "Term slug", "string"),
        ScopeVariable("url", "Term URL", "string"),
        ScopeVariable("permalink", "Full absolute URL", "string"),
        ScopeVariable("entries_count", "Number of entries with this term", "integer"),
        ScopeVariable("taxonomy", "Taxonomy handle", "string"),
    )

    /** Search result fields. */
    private val SEARCH_FIELDS: List<ScopeVariable> = listOf(
        ScopeVariable("title", "Result title", "string"),
        ScopeVariable("url", "Result URL", "string"),
        ScopeVariable("search_score", "Relevance score", "float"),
    )

    /** Assets fields. */
    private val ASSET_FIELDS: List<ScopeVariable> = listOf(
        ScopeVariable("url", "Asset URL", "string"),
        ScopeVariable("permalink", "Full absolute asset URL", "string"),
        ScopeVariable("path", "Asset path within container", "string"),
        ScopeVariable("basename", "Filename with extension", "string"),
        ScopeVariable("filename", "Filename without extension", "string"),
        ScopeVariable("extension", "File extension", "string"),
        ScopeVariable("size", "File size in bytes", "integer"),
        ScopeVariable("size_bytes", "File size in bytes", "integer"),
        ScopeVariable("size_kilobytes", "File size in KB", "float"),
        ScopeVariable("size_megabytes", "File size in MB", "float"),
        ScopeVariable("last_modified", "Last modified timestamp", "date"),
        ScopeVariable("width", "Image width in pixels", "integer"),
        ScopeVariable("height", "Image height in pixels", "integer"),
        ScopeVariable("alt", "Alt text", "string"),
        ScopeVariable("is_image", "True if file is an image", "boolean"),
    )

    /** Form submission fields. */
    private val FORM_FIELDS: List<ScopeVariable> = listOf(
        ScopeVariable("success", "True after successful submission", "boolean"),
        ScopeVariable("errors", "Validation error messages", "array"),
        ScopeVariable("fields", "Form field definitions", "array"),
        ScopeVariable("old", "Previously submitted values", "object"),
    )

    private val scopesByTag: Map<String, List<ScopeVariable>> = mapOf(
        "nav" to LOOP_VARIABLES + NAV_FIELDS,
        "taxonomy" to LOOP_VARIABLES + TAXONOMY_FIELDS,
        "search" to LOOP_VARIABLES + SEARCH_FIELDS,
        "assets" to LOOP_VARIABLES + ASSET_FIELDS,
        "form" to FORM_FIELDS,
        "foreach" to LOOP_VARIABLES,
        "loop" to LOOP_VARIABLES,
    )

    /**
     * Returns scope variables available inside a tag pair block.
     * Uses the root tag name (before ':').
     */
    fun forTag(tagName: String): List<ScopeVariable> {
        return forTag(project = null, tagName = tagName, tagText = tagName)
    }

    fun forTag(project: Project?, tagName: String, tagText: String = tagName): List<ScopeVariable> {
        val rootName = tagName.substringBefore(':')
        return when (rootName) {
            "collection" -> LOOP_VARIABLES + ENTRY_FIELDS + customEntryFields(project, tagName, tagText)
            else -> scopesByTag[rootName].orEmpty()
        }
    }

    fun hasScopeVariables(tagName: String): Boolean {
        val rootName = tagName.substringBefore(':')
        return rootName == "collection" || scopesByTag.containsKey(rootName)
    }

    internal fun extractCollectionHandles(tagName: String, tagText: String): List<String> {
        val namespacedHandle = tagName.substringAfter(':', "")
            .takeIf { it.isNotBlank() }
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (namespacedHandle != null) {
            return listOf(namespacedHandle)
        }

        val parameterHandles = collectionHandlePattern.findAll(tagText)
            .flatMap { match ->
                match.groupValues[3].split('|').asSequence()
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        return parameterHandles.distinct()
    }

    internal fun customEntryFields(
        project: Project?,
        tagName: String,
        tagText: String,
        indexedEntryFieldsByCollection: Map<String, List<String>>? = null
    ): List<ScopeVariable> {
        val fieldIndex = indexedEntryFieldsByCollection
            ?: project?.let { StatamicProjectCollections.getInstance(it).index.entryFieldsByCollection }
            ?: emptyMap()
        if (fieldIndex.isEmpty()) return emptyList()

        val collectionHandles = extractCollectionHandles(tagName, tagText)
        val fieldNames = if (collectionHandles.isEmpty()) {
            fieldIndex.values.asSequence().flatten()
        } else {
            collectionHandles.asSequence().flatMap { fieldIndex[it].orEmpty().asSequence() }
        }

        return fieldNames
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .filterNot { builtInScopeVariableNames.contains(it) }
            .map { fieldName ->
                ScopeVariable(
                    name = fieldName,
                    description = customFieldDescription(collectionHandles),
                    type = "field"
                )
            }
            .toList()
    }

    private val collectionHandlePattern = Regex("""\b(from|in|handle|collection)\s*=\s*(['"])(.*?)\2""")

    private val builtInScopeVariableNames = (
        LOOP_VARIABLES +
            ENTRY_FIELDS +
            NAV_FIELDS +
            TAXONOMY_FIELDS +
            SEARCH_FIELDS +
            ASSET_FIELDS +
            FORM_FIELDS
        ).mapTo(linkedSetOf()) { it.name }

    private fun customFieldDescription(collectionHandles: List<String>): String {
        return when (collectionHandles.size) {
            0 -> "Indexed blueprint field"
            1 -> "Blueprint field from ${collectionHandles.first()}"
            else -> "Blueprint field from ${collectionHandles.joinToString(", ")}"
        }
    }
}
