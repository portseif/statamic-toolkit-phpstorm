package com.antlers.support.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatamicSnippetTemplatesTest {
    @Test
    fun exposesExpectedContentQueryTemplates() {
        assertEquals(
            listOf(
                "Entry Query",
                "Single Entry Query",
                "Paginated Entry Query",
                "Global Set Lookup"
            ),
            StatamicSnippetTemplates.contentQueries.map { it.title }
        )
    }

    @Test
    fun normalizesControllerClassNames() {
        assertEquals("ExampleController", StatamicSnippetTemplates.normalizeControllerClassName("Example"))
        assertEquals("BlogPostsController", StatamicSnippetTemplates.normalizeControllerClassName("blog posts"))
        assertEquals("ExampleController", StatamicSnippetTemplates.normalizeControllerClassName("ExampleController"))
        assertNull(StatamicSnippetTemplates.normalizeControllerClassName("///"))
    }

    @Test
    fun buildsBasicControllerTemplate() {
        val template = StatamicSnippetTemplates.buildBasicController("ExampleController")

        assertTrue(template.contains("class ExampleController extends Controller"))
        assertTrue(template.contains("return view('myview', \$data);"))
    }

    @Test
    fun buildsAntlersViewControllerTemplate() {
        val template = StatamicSnippetTemplates.buildAntlersViewController("ExampleController")

        assertTrue(template.contains("use Statamic\\View\\View;"))
        assertTrue(template.contains("->template('myview')"))
        assertTrue(template.contains("->layout('mylayout')"))
    }
}
