package com.antlers.support.statusbar

import org.junit.Assert.assertEquals
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
}
