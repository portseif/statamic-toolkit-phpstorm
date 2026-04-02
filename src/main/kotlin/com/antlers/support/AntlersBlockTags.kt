package com.antlers.support

/**
 * Tags known to be block tags that accept a closing {{ /tag }} pair.
 * Shared across the formatter, enter handler, and typed handler.
 */
object AntlersBlockTags {
    val NAMES: Set<String> = setOf(
        "collection", "nav", "taxonomy", "form", "search", "assets",
        "cache", "nocache", "scope", "section", "slot", "foreach", "loop",
        "noparse", "user", "users", "session", "entries", "terms",
        "get_content", "get_errors", "get_files", "relate", "yield",
        "installed", "member", "oauth", "protect", "set", "obfuscate",
        "cookie", "user_groups", "user_roles",
        // Nested loop/group tags used inside collection and other contexts
        "entry", "groups", "group", "items", "values", "options", "fields",
    )

    fun isBlockTag(tagName: String): Boolean {
        return tagName.substringBefore(':') in NAMES
    }
}
