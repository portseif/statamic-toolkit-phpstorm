package com.antlers.support.statamic

import java.io.File

internal object StatamicBlueprintFieldScanner {
    private val yamlFilePattern = Regex(""".+\.(yaml|yml)$""", RegexOption.IGNORE_CASE)
    private val fieldHandlePattern = Regex(
        """^\s*handle:\s*["']?([A-Za-z0-9_-]+)["']?\s*(?:#.*)?$""",
        RegexOption.MULTILINE
    )

    fun scanCollectionEntryFields(basePath: String): Map<String, List<String>> {
        val root = File(basePath, "resources/blueprints/collections")
        if (!root.isDirectory) return emptyMap()

        val indexed = linkedMapOf<String, List<String>>()
        val collectionNodes = root.listFiles().orEmpty()
            .sortedBy { it.name.lowercase() }

        for (node in collectionNodes) {
            val handle = when {
                node.isDirectory -> node.name
                isYamlFile(node) -> node.nameWithoutExtension
                else -> null
            } ?: continue

            val fields = scanNode(node)
            if (fields.isNotEmpty()) {
                indexed[handle] = fields
            }
        }

        return indexed
    }

    fun extractFieldHandles(yaml: String): List<String> {
        return fieldHandlePattern.findAll(yaml)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }

    private fun scanNode(node: File): List<String> {
        val files = when {
            node.isDirectory -> node.walkTopDown().filter(::isYamlFile).toList()
            isYamlFile(node) -> listOf(node)
            else -> emptyList()
        }

        val handles = linkedSetOf<String>()
        for (file in files) {
            handles += extractFieldHandles(file.readText())
        }

        return handles.toList()
    }

    private fun isYamlFile(file: File): Boolean {
        return file.isFile && yamlFilePattern.matches(file.name)
    }
}

internal fun StatamicDriver.displayName(): String = when (this) {
    StatamicDriver.ELOQUENT -> "Eloquent (database)"
    StatamicDriver.FLAT_FILE -> "Default (flat-file)"
    StatamicDriver.UNKNOWN -> "—"
}

internal val StatamicIndex.entryFields: List<String>
    get() = entryFieldsByCollection.values.asSequence()
        .flatten()
        .distinct()
        .sorted()
        .toList()

internal fun StatamicIndex.totalResources(): Int {
    return collections.size +
        navigations.size +
        taxonomies.size +
        globalSets.size +
        forms.size +
        assetContainers.size
}

internal fun StatamicIndex.readyStatusMessage(): String {
    val resourceSummary = "${totalResources()} resource(s) indexed"
    val fieldCount = entryFields.size
    return if (fieldCount > 0) {
        "$resourceSummary, $fieldCount entry field(s)"
    } else {
        resourceSummary
    }
}
