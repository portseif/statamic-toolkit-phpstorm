package com.antlers.support.partials

import com.intellij.openapi.vfs.VirtualFile

object AntlersPartialPaths {
    private val extensions = listOf("antlers.html", "antlers.php", "blade.php", "html")

    fun extensions(): List<String> = extensions

    fun matches(file: VirtualFile, partialPath: String): Boolean {
        val requestedVariants = variants(partialPath)
        if (requestedVariants.isEmpty()) return false

        return extractLookupPaths(file).any(requestedVariants::contains)
    }

    fun lookupPath(file: VirtualFile): String? {
        return extractLookupPaths(file)
            .minWithOrNull(compareBy<String>({ pathPenalty(it) }, { it.length }))
    }

    fun candidateFileNames(partialPath: String): Set<String> {
        return variants(partialPath)
            .flatMapTo(linkedSetOf()) { variant ->
                val lastSegment = variant.substringAfterLast("/")
                val names = buildSet {
                    add(lastSegment)
                    add(lastSegment.removePrefix("_"))
                    add("_${lastSegment.removePrefix("_")}")
                }

                names.flatMap { name -> extensions.map { "$name.$it" } }
            }
    }

    private fun extractLookupPaths(file: VirtualFile): Set<String> {
        val path = file.path.replace('\\', '/')
        val viewsIndex = path.lastIndexOf("/views/")
        if (viewsIndex < 0) return emptySet()

        val relativePath = path.substring(viewsIndex + "/views/".length)
        val withoutExtension = extensions.fold(relativePath) { current, extension ->
            current.removeSuffix(".$extension")
        }

        return variants(withoutExtension)
    }

    private fun variants(path: String): Set<String> {
        val normalized = path.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return emptySet()

        val result = linkedSetOf(normalized)
        val withoutPartialsPrefix = normalized.removePrefix("partials/")
        result += withoutPartialsPrefix

        val underscored = withLastSegmentUnderscore(normalized)
        val nonUnderscored = withoutLastSegmentUnderscore(normalized)

        result += underscored
        result += nonUnderscored
        result += underscored.removePrefix("partials/")
        result += nonUnderscored.removePrefix("partials/")

        return result.filterTo(linkedSetOf()) { it.isNotBlank() }
    }

    private fun withLastSegmentUnderscore(path: String): String {
        val lastSegment = path.substringAfterLast("/")
        if (lastSegment.startsWith("_")) return path

        val prefix = path.substringBeforeLast("/", "")
        val underscored = "_$lastSegment"
        return if (prefix.isEmpty()) underscored else "$prefix/$underscored"
    }

    private fun withoutLastSegmentUnderscore(path: String): String {
        val lastSegment = path.substringAfterLast("/")
        val normalizedLastSegment = lastSegment.removePrefix("_")

        val prefix = path.substringBeforeLast("/", "")
        return if (prefix.isEmpty()) normalizedLastSegment else "$prefix/$normalizedLastSegment"
    }

    private fun pathPenalty(path: String): Int {
        var penalty = 0
        if (path.startsWith("partials/")) penalty += 10
        if (path.substringAfterLast("/").startsWith("_")) penalty += 5
        return penalty
    }
}
