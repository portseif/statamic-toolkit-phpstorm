package com.antlers.support.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatamicStatusBarWidgetFactoryTest {
    @Test
    fun leavesShortListsUnchanged() {
        assertEquals(
            "pages, posts",
            summarizeStatusBarHandles(listOf("pages", "posts"))
        )
    }

    @Test
    fun summarizesLongListsWithHiddenCount() {
        assertEquals(
            "main, footer_product, footer_use_cases +1",
            summarizeStatusBarHandles(
                listOf("main", "footer_product", "footer_use_cases", "footer_company"),
                maxLength = 42
            )
        )
    }

    @Test
    fun truncatesSingleLongHandleWhenNeeded() {
        assertEquals(
            "extremely_long_navigation_handle_th…",
            summarizeStatusBarHandles(
                listOf("extremely_long_navigation_handle_that_keeps_going"),
                maxLength = 36
            )
        )
    }

    @Test
    fun formatsRecentIndexTimeAsJustNow() {
        assertEquals(
            "Last indexed just now",
            formatLastIndexedText(lastIndexedAtMillis = 1_000L, nowMillis = 30_000L)
        )
    }

    @Test
    fun formatsOlderIndexTimeInHours() {
        assertEquals(
            "Last indexed 2h ago",
            formatLastIndexedText(lastIndexedAtMillis = 1_000L, nowMillis = 7_300_000L)
        )
    }

    @Test
    fun returnsNullWhenIndexTimeIsUnknown() {
        assertNull(formatLastIndexedText(lastIndexedAtMillis = null, nowMillis = 10_000L))
    }

    @Test
    fun emptyStateEncouragesRefreshBeforeIndexing() {
        assertEquals(
            "Run Refresh to index Statamic resources.",
            resourcesEmptyStateText(status = com.antlers.support.statamic.IndexingStatus.NOT_STARTED, totalResources = 0)
        )
    }

    @Test
    fun emptyStateShowsReadyProjectMessageWhenNothingWasFound() {
        assertEquals(
            "No Statamic resources found in this project.",
            resourcesEmptyStateText(status = com.antlers.support.statamic.IndexingStatus.READY, totalResources = 0)
        )
    }
}
