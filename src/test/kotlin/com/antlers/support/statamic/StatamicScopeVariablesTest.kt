package com.antlers.support.statamic

import org.junit.Assert.assertEquals
import org.junit.Test

class StatamicScopeVariablesTest {
    @Test
    fun extractsCollectionHandleFromNamespacedTag() {
        assertEquals(
            listOf("posts"),
            StatamicScopeVariables.extractCollectionHandles(
                tagName = "collection:posts",
                tagText = "{{ collection:posts }}"
            )
        )
    }

    @Test
    fun extractsCollectionHandlesFromParameters() {
        assertEquals(
            listOf("pages", "posts"),
            StatamicScopeVariables.extractCollectionHandles(
                tagName = "collection",
                tagText = "{{ collection from=\"pages|posts\" }}"
            )
        )
    }

    @Test
    fun addsOnlyCustomFieldsForSpecificCollection() {
        val fields = StatamicScopeVariables.customEntryFields(
            project = null,
            tagName = "collection:posts",
            tagText = "{{ collection:posts }}",
            indexedEntryFieldsByCollection = mapOf(
                "posts" to listOf("excerpt", "cta_text", "hero_text"),
                "pages" to listOf("promo_copy")
            )
        )

        assertEquals(listOf("cta_text", "hero_text"), fields.map { it.name })
    }

    @Test
    fun fallsBackToAllIndexedFieldsWhenCollectionHandleIsNotSpecified() {
        val fields = StatamicScopeVariables.customEntryFields(
            project = null,
            tagName = "collection",
            tagText = "{{ collection }}",
            indexedEntryFieldsByCollection = mapOf(
                "posts" to listOf("hero_text"),
                "pages" to listOf("promo_copy")
            )
        )

        assertEquals(listOf("hero_text", "promo_copy"), fields.map { it.name })
    }
}
