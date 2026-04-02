package com.antlers.support.statamic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StatamicBlueprintFieldScannerTest {
    @Test
    fun extractsFieldHandlesFromBlueprintYaml() {
        val yaml = """
            tabs:
              main:
                sections:
                  -
                    fields:
                      -
                        handle: title
                        field:
                          type: text
                      -
                        handle: hero_text
                        field:
                          type: textarea
        """.trimIndent()

        assertEquals(
            listOf("hero_text", "title"),
            StatamicBlueprintFieldScanner.extractFieldHandles(yaml)
        )
    }

    @Test
    fun scansCollectionBlueprintDirectoriesAndSingleFiles() {
        val tempDir = Files.createTempDirectory("statamic-blueprints").toFile()
        try {
            val postsDir = File(tempDir, "resources/blueprints/collections/posts").apply { mkdirs() }
            File(postsDir, "post.yaml").writeText(
                """
                    tabs:
                      main:
                        sections:
                          -
                            fields:
                              -
                                handle: title
                              -
                                handle: hero_text
                """.trimIndent()
            )

            val collectionsRoot = File(tempDir, "resources/blueprints/collections").apply { mkdirs() }
            File(collectionsRoot, "pages.yaml").writeText(
                """
                    sections:
                      main:
                        fields:
                          -
                            handle: seo_description
                """.trimIndent()
            )

            assertEquals(
                mapOf(
                    "pages" to listOf("seo_description"),
                    "posts" to listOf("hero_text", "title")
                ),
                StatamicBlueprintFieldScanner.scanCollectionEntryFields(tempDir.absolutePath)
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun formatsFlatFileDriverLabelAsDefault() {
        assertEquals("Default (flat-file)", StatamicDriver.FLAT_FILE.displayName())
    }
}
