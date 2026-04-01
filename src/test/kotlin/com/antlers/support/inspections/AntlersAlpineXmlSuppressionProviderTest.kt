package com.antlers.support.inspections

import com.intellij.lang.html.HTMLLanguage
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersAlpineXmlSuppressionProviderTest : BasePlatformTestCase() {

    fun testSuppressesXmlUnboundNamespaceForAlpinePseudoNamespaceAttributes() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div
                x-transition:enter="transition ease-out duration-300"
                x-on:keydown.escape.window="closeDrawer()"
                x-bind:class="drawer ? 'open' : ''"
            ></div>
            """.trimIndent()
        )

        val provider = AntlersAlpineXmlSuppressionProvider()

        assertTrue(provider.isProviderAvailable(myFixture.file))
        assertTrue(provider.isSuppressedFor(htmlElementAt("x-transition:enter"), "XmlUnboundNsPrefix"))
        assertTrue(provider.isSuppressedFor(htmlElementAt("x-on:keydown.escape.window"), "XmlUnboundNsPrefix"))
        assertTrue(provider.isSuppressedFor(htmlElementAt("x-bind:class"), "XmlUnboundNsPrefix"))
    }

    fun testDoesNotSuppressRegularNamespacedAttributes() {
        myFixture.configureByText(
            "demo.antlers.html",
            """<div foo:bar="baz"></div>"""
        )

        val provider = AntlersAlpineXmlSuppressionProvider()

        assertFalse(provider.isSuppressedFor(htmlElementAt("foo:bar"), "XmlUnboundNsPrefix"))
    }

    fun testDoesNotApplyOutsideAntlersFiles() {
        myFixture.configureByText(
            "demo.html",
            """<div x-transition:enter="transition ease-out duration-300"></div>"""
        )

        val provider = AntlersAlpineXmlSuppressionProvider()

        assertFalse(provider.isProviderAvailable(myFixture.file))
        assertFalse(provider.isSuppressedFor(htmlElementAt("x-transition:enter"), "XmlUnboundNsPrefix"))
    }

    private fun htmlElementAt(marker: String) = requireNotNull(
        myFixture.file.viewProvider.findElementAt(myFixture.file.text.indexOf(marker), HTMLLanguage.INSTANCE)
    ) { "Expected HTML PSI element for marker: $marker" }
}
