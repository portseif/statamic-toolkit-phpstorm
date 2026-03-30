package com.antlers.support.statamic

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatamicCatalogTest {
    @Test
    fun normalizesOfficialCatalogNamesToAntlersSyntax() {
        assertNotNull(StatamicCatalog.findTag("user_groups"))
        assertNull(StatamicCatalog.findTag("user-groups"))

        assertNotNull(StatamicCatalog.findModifier("where_in"))
        assertNull(StatamicCatalog.findModifier("where-in"))

        assertNotNull(StatamicCatalog.findVariable("csrf_token"))
        assertNotNull(StatamicCatalog.findVariable("current_url"))
    }
}
