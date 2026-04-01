package com.antlers.support.formatting

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFormattingPostProcessorTest : BasePlatformTestCase() {

    fun testReformatAlignsElseWithIfAndIndentsStandaloneAntlersTagsInsideBranch() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ if site:environment==='production'}}
            {{ else }}
            {{ partial:partials/sections/contact-form }}
            {{ /if }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ if site:environment === 'production' }}
            {{ else }}
                {{ partial:partials/sections/contact-form }}
            {{ /if }}
            """.trimIndent()
        )
    }

    fun testReformatIndentsNestedConditionalBranches() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ if outer }}
            {{ if inner }}
            {{ partial:components/admin }}
            {{ /if }}
            {{ else }}
            {{ partial:components/fallback }}
            {{ /if }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ if outer }}
                {{ if inner }}
                    {{ partial:components/admin }}
                {{ /if }}
            {{ else }}
                {{ partial:components/fallback }}
            {{ /if }}
            """.trimIndent()
        )
    }

    fun testReformatIsIdempotentForConditionalIndentation() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div>
                {{ if logged_in }}
                    {{ partial:components/account }}
                {{ /if }}
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()
        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div>
                {{ if logged_in }}
                    {{ partial:components/account }}
                {{ /if }}
            </div>
            """.trimIndent()
        )
    }

    fun testReformatDoesNotNestSequentialStandalonePartials() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <main>
                {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <main>
                {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )
    }

    fun testReformatFlattensSequentialStandalonePartialsAtRootLevel() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ partial:partials/sections/hero }}
            {{ partial:partials/sections/logo-cloud }}
            {{ partial:partials/sections/bento }}
            """.trimIndent()
        )
    }

    fun testReformatFlattensSequentialStandalonePartialsInsideHtml() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <main>
                {{ partial:partials/sections/hero }}
                    {{ partial:partials/sections/logo-cloud }}
                    {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <main>
                {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )
    }

    fun testReformatPreservesHtmlParentIndentAroundStandaloneAntlersBlocks() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div class="font-display">
            <header>
            {{ partial:partials/nav }}
            </header>

            <main id="main-content">
            {{ partial:partials/sections/hero }}
            {{ partial:partials/sections/logo-cloud }}
            {{ partial:partials/sections/bento }}
            </main>

            {{ if site:environment==='production'}}
            {{ else }}
            {{ partial:partials/sections/contact-form }}
            {{ /if }}

            {{ partial:partials/sections/footer }}
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div class="font-display">
                <header>
                    {{ partial:partials/nav }}
                </header>

                <main id="main-content">
                    {{ partial:partials/sections/hero }}
                    {{ partial:partials/sections/logo-cloud }}
                    {{ partial:partials/sections/bento }}
                </main>

                {{ if site:environment === 'production' }}
                {{ else }}
                    {{ partial:partials/sections/contact-form }}
                {{ /if }}

                {{ partial:partials/sections/footer }}
            </div>
            """.trimIndent()
        )
    }

    private fun reformatCurrentFile() {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
