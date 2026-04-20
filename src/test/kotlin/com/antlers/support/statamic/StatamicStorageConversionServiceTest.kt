package com.antlers.support.statamic

import com.intellij.openapi.diagnostic.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class StatamicStorageConversionServiceTest {
    @Test
    fun rewritesExistingEnvKeysAndAppendsMissingOnes() {
        val current = """
            APP_ENV=local
            DB_CONNECTION=sqlite
            DB_DATABASE=/tmp/old.sqlite
        """.trimIndent()

        val rewritten = rewriteEnvContent(
            current,
            mapOf(
                "DB_CONNECTION" to "mysql",
                "DB_HOST" to "127.0.0.1",
                "DB_DATABASE" to "statamic",
            )
        )

        assertTrue(rewritten.contains("DB_CONNECTION=mysql"))
        assertTrue(rewritten.contains("DB_HOST=127.0.0.1"))
        assertTrue(rewritten.contains("DB_DATABASE=statamic"))
        assertTrue(rewritten.contains("APP_ENV=local"))
    }

    @Test
    fun rewritesGitIgnoreForDatabaseAndFlatFileTargets() {
        val original = """
            *.log
            /vendor
        """.trimIndent()

        val database = rewriteGitIgnoreContent(original, StorageConversionTarget.DATABASE)
        assertTrue(database.contains("/storage/statamic-toolkit/"))
        assertTrue(database.contains("/content/collections/"))
        assertTrue(database.contains("/resources/sites.yaml"))
        assertTrue(database.contains("/vendor"))

        val flatFile = rewriteGitIgnoreContent(database, StorageConversionTarget.FLAT_FILE)
        assertTrue(flatFile.contains("/storage/statamic-toolkit/"))
        assertFalse(flatFile.contains("/content/collections/"))
        assertFalse(flatFile.contains("/resources/sites.yaml"))
        assertTrue(flatFile.contains("/vendor"))
        assertEquals(1, "# Antlers Support storage conversion".toRegex().findAll(flatFile).count())
    }

    @Test
    fun buildsFlatFileSnapshotFromStatamicDirectories() {
        val root = Files.createTempDirectory("statamic-storage-snapshot")
        try {
            Files.createDirectories(root.resolve("content/collections/posts"))
            Files.writeString(root.resolve("content/collections/posts/hello-world.md"), "---\ntitle: Hello\n---")
            Files.writeString(root.resolve("content/collections/posts/another.md"), "---\ntitle: Another\n---")
            Files.writeString(root.resolve("content/collections/pages.yaml"), "title: Pages")

            Files.createDirectories(root.resolve("resources/forms"))
            Files.writeString(root.resolve("resources/forms/contact.yaml"), "title: Contact")

            Files.createDirectories(root.resolve("content/globals"))
            Files.writeString(root.resolve("content/globals/site.yaml"), "title: Site")

            Files.createDirectories(root.resolve("content/navigation"))
            Files.writeString(root.resolve("content/navigation/main.yaml"), "title: Main")

            Files.createDirectories(root.resolve("content/taxonomies/tags"))
            Files.writeString(root.resolve("content/taxonomies/tags.yaml"), "title: Tags")
            Files.writeString(root.resolve("content/taxonomies/tags/laravel.md"), "---\ntitle: Laravel\n---")

            Files.createDirectories(root.resolve("resources/blueprints/collections/posts"))
            Files.writeString(root.resolve("resources/blueprints/collections/posts/post.yaml"), "tabs: {}")

            val snapshot = buildFlatFileSnapshot(root)

            assertEquals(2, snapshot.metrics.getValue("collections").count)
            assertEquals(2, snapshot.metrics.getValue("entries").count)
            assertEquals(1, snapshot.metrics.getValue("forms").count)
            assertEquals(1, snapshot.metrics.getValue("global_sets").count)
            assertEquals(1, snapshot.metrics.getValue("navigations").count)
            assertEquals(1, snapshot.metrics.getValue("taxonomies").count)
            assertEquals(1, snapshot.metrics.getValue("terms").count)
            assertEquals(1, snapshot.metrics.getValue("blueprints").count)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun verifiesMergeWhenSourceIdentifiersArePresentInTarget() {
        val source = StorageSnapshot(
            driver = StatamicDriver.FLAT_FILE,
            locationDescription = "source",
            sizeBytes = 10,
            availableSpaceBytes = null,
            metrics = mapOf(
                "collections" to StorageMetric(
                    key = "collections",
                    label = "Collections",
                    count = 1,
                    comparisonMode = StorageComparisonMode.EXACT_IDENTIFIERS,
                    identifiers = listOf("posts"),
                ),
                "entries" to StorageMetric(
                    key = "entries",
                    label = "Entries",
                    count = 2,
                    comparisonMode = StorageComparisonMode.COUNT_ONLY,
                ),
            ),
        )
        val targetBefore = StorageSnapshot(
            driver = StatamicDriver.ELOQUENT,
            locationDescription = "target-before",
            sizeBytes = 10,
            availableSpaceBytes = null,
            metrics = mapOf(
                "collections" to StorageMetric(
                    key = "collections",
                    label = "Collections",
                    count = 1,
                    comparisonMode = StorageComparisonMode.EXACT_IDENTIFIERS,
                    identifiers = listOf("pages"),
                ),
                "entries" to StorageMetric(
                    key = "entries",
                    label = "Entries",
                    count = 1,
                    comparisonMode = StorageComparisonMode.COUNT_ONLY,
                ),
            ),
        )
        val targetAfter = targetBefore.copy(
            metrics = mapOf(
                "collections" to StorageMetric(
                    key = "collections",
                    label = "Collections",
                    count = 2,
                    comparisonMode = StorageComparisonMode.EXACT_IDENTIFIERS,
                    identifiers = listOf("pages", "posts"),
                ),
                "entries" to StorageMetric(
                    key = "entries",
                    label = "Entries",
                    count = 3,
                    comparisonMode = StorageComparisonMode.COUNT_ONLY,
                ),
            ),
        )

        val messages = verifyStorageTransfer(
            source = source,
            targetBefore = targetBefore,
            targetAfter = targetAfter,
            resolution = StorageConflictResolution.MERGE,
        )

        assertTrue(messages.any { it.contains("Collections") })
        assertTrue(messages.any { it.contains("Entries") })
    }

    @Test
    fun overwriteVerificationReportsMismatchedIdentifiers() {
        val source = StorageSnapshot(
            driver = StatamicDriver.FLAT_FILE,
            locationDescription = "source",
            sizeBytes = 10,
            availableSpaceBytes = null,
            metrics = mapOf(
                "collections" to StorageMetric(
                    key = "collections",
                    label = "Collections",
                    count = 1,
                    comparisonMode = StorageComparisonMode.EXACT_IDENTIFIERS,
                    identifiers = listOf("posts"),
                ),
            ),
        )
        val target = StorageSnapshot(
            driver = StatamicDriver.ELOQUENT,
            locationDescription = "target",
            sizeBytes = 10,
            availableSpaceBytes = null,
            metrics = mapOf(
                "collections" to StorageMetric(
                    key = "collections",
                    label = "Collections",
                    count = 1,
                    comparisonMode = StorageComparisonMode.EXACT_IDENTIFIERS,
                    identifiers = listOf("pages"),
                ),
            ),
        )

        val messages = verifyStorageTransfer(
            source = source,
            targetBefore = target,
            targetAfter = target,
            resolution = StorageConflictResolution.OVERWRITE,
        )
        assertTrue(messages.any { it.contains("expected") && it.contains("identifier") })
    }

    @Test
    fun extractsActionableLaravelCommandErrorsInsteadOfEchoedCommandLines() {
        val output = """
            "/Users/portseif/Library/Application Support/Herd/bin/php" please eloquent:export-entries --no-interaction
            Exporting origin entries
             0/8 [░░░░░░░░░░░░░░░░░░░░░░░░░░░░]   0%
               Error

              Call to a member function entryClass() on null

              at vendor/statamic/cms/src/Entries/Entry.php:158
                154▕             ${'$'}this->collection = ${'$'}collection->handle();

                  +19 vendor frames

              20  please:18
                  Illuminate\Foundation\Application::handleCommand(Object(Symfony\Component\Console\Input\ArgvInput))
        """.trimIndent()

        assertEquals(
            "Call to a member function entryClass() on null",
            extractCommandError(output)
        )
        assertTrue(
            suggestCommandErrorHint(output)?.contains("orphaned entries") == true
        )
    }

    @Test
    fun extractsIncompleteObjectExportErrorsInsteadOfGenericErrorMarkers() {
        val output = """
            "/Users/portseif/Library/Application Support/Herd/bin/php" please eloquent:export-collections --no-interaction --force
             0/3 [░░░░░░░░░░░░░░░░░░░░░░░░░░░░]   0%
               Error

              The script tried to call a method on an incomplete object. Please ensure that the class definition "Statamic\Eloquent\Collections\Collection" of the object you are trying to operate on was loaded _before_ unserialize() gets called or provide an autoloader to load the class definition

              at vendor/statamic/cms/src/Stache/Stores/BasicStore.php:27
                 23▕             return null;
                 24▕         }
                 25▕
                 26▕         if (${'$'}item = ${'$'}this->getCachedItem(${'$'}key)) {
              ➜  27▕             if (method_exists(${'$'}item, 'syncOriginal')) {
                 28▕                 ${'$'}item->syncOriginal();

                  +2 vendor frames

              34  please:18
                  Illuminate\Foundation\Application::handleCommand(Object(Symfony\Component\Console\Input\ArgvInput))
        """.trimIndent()

        val summary = extractCommandError(output)
        assertTrue(summary != "Error")
        assertTrue(summary.startsWith("The script tried to call a method on an incomplete object."))
        assertTrue(summary.contains("Statamic\\Eloquent\\Collections\\Collection"))
        assertTrue(
            suggestCommandErrorHint(output)?.contains("stale cached Eloquent collection") == true
        )
    }

    @Test
    fun convertsFlatFileProjectToDatabaseWithBackupsVerificationAndDriverRewrite() {
        val root = Files.createTempDirectory("statamic-storage-convert")
        try {
            Files.createDirectories(root.resolve("config/statamic"))
            Files.createDirectories(root.resolve("content/collections/posts"))
            Files.writeString(root.resolve("content/collections/posts/hello-world.md"), "---\ntitle: Hello\n---")
            Files.writeString(root.resolve(".env"), "APP_ENV=local\n")
            Files.writeString(root.resolve(".gitignore"), "/vendor\n")
            Files.writeString(root.resolve("artisan"), "#!/usr/bin/env php\n")
            Files.writeString(root.resolve("please"), "#!/usr/bin/env php\n")

            val executor = FakeCommandExecutor(root)
            val engine = StorageConversionEngine(
                basePath = root,
                phpPath = "php",
                logger = Logger.getInstance("storage-conversion-test"),
                commandExecutor = executor,
            )
            val progressEvents = mutableListOf<StorageConversionProgress>()

            val result = engine.convert(
                StorageConversionRequest(
                    target = StorageConversionTarget.DATABASE,
                    databaseConfig = DatabaseConnectionConfig(
                        connection = "mysql",
                        host = "127.0.0.1",
                        port = "3306",
                        database = "statamic",
                        username = "root",
                        password = "secret",
                    ),
                ),
                progressEvents::add,
            )

            assertTrue(result.success)
            assertNotNull(result.backupDirectory)
            assertNotNull(result.logFile)
            assertTrue(Files.exists(result.backupDirectory))
            assertTrue(Files.exists(result.logFile))
            assertTrue(progressEvents.any { it.phase == StorageConversionPhase.COMPLETED })
            assertTrue(result.verificationMessages.any { it.contains("Collections") })
            assertTrue(result.verificationMessages.any { it.contains("Entries") })
            assertTrue(Files.readString(root.resolve(".env")).contains("DB_CONNECTION=mysql"))
            assertTrue(Files.readString(root.resolve(".env")).contains("DB_DATABASE=statamic"))
            assertTrue(Files.readString(root.resolve("config/statamic/eloquent-driver.php")).contains("'driver' => 'eloquent'"))
            assertTrue(Files.readString(root.resolve(".gitignore")).contains("/content/collections/"))
            assertTrue(Files.readString(root.resolve(".gitignore")).contains("/storage/statamic-toolkit/"))
            assertTrue(executor.executedCommands.contains("install:eloquent-driver"))
            assertTrue(executor.installDriverArgs.contains("--repositories=tokens"))
            assertTrue(executor.installDriverArgs.contains("--no-interaction"))
            assertTrue(executor.executedCommands.contains("eloquent:import-entries"))
            assertTrue(executor.executedCommands.contains("stache:clear"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun convertsDatabaseProjectToFlatFilesAfterClearingStacheBeforeExport() {
        val root = Files.createTempDirectory("statamic-storage-flat-file")
        try {
            Files.createDirectories(root.resolve("config/statamic"))
            Files.writeString(root.resolve(".env"), "APP_ENV=local\n")
            Files.writeString(
                root.resolve(".gitignore"),
                rewriteGitIgnoreContent("/vendor\n", StorageConversionTarget.DATABASE)
            )
            Files.writeString(root.resolve("artisan"), "#!/usr/bin/env php\n")
            Files.writeString(root.resolve("please"), "#!/usr/bin/env php\n")

            val executor = FakeCommandExecutor(
                root = root,
                initialDatabasePopulated = true,
                requireStacheClearBeforeExportCollections = true,
            )
            executor.writeDriverConfig("eloquent")
            val engine = StorageConversionEngine(
                basePath = root,
                phpPath = "php",
                logger = Logger.getInstance("storage-conversion-test"),
                commandExecutor = executor,
            )
            val progressEvents = mutableListOf<StorageConversionProgress>()

            val result = engine.convert(
                StorageConversionRequest(target = StorageConversionTarget.FLAT_FILE),
                progressEvents::add,
            )

            assertTrue(result.success)
            assertNotNull(result.backupDirectory)
            assertNotNull(result.logFile)
            assertTrue(progressEvents.any { it.phase == StorageConversionPhase.COMPLETED })
            assertTrue(result.verificationMessages.any { it.contains("Collections") })
            assertTrue(result.verificationMessages.any { it.contains("Entries") })
            assertTrue(Files.exists(root.resolve("content/collections/posts/hello-world.md")))
            assertTrue(Files.readString(root.resolve("config/statamic/eloquent-driver.php")).contains("'driver' => 'file'"))
            assertTrue(Files.readString(root.resolve(".gitignore")).contains("/storage/statamic-toolkit/"))
            assertFalse(Files.readString(root.resolve(".gitignore")).contains("/content/collections/"))

            val stacheIndex = executor.executedCommands.indexOf("stache:clear")
            val exportCollectionsIndex = executor.executedCommands.indexOf("eloquent:export-collections")
            assertTrue(stacheIndex >= 0)
            assertTrue(exportCollectionsIndex > stacheIndex)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

private class FakeCommandExecutor(
    private val root: Path,
    initialDatabasePopulated: Boolean = false,
    private val requireStacheClearBeforeExportCollections: Boolean = false,
) : CommandExecutor {
    val executedCommands = mutableListOf<String>()
    var installDriverArgs: List<String> = emptyList()

    private var databasePopulated = initialDatabasePopulated
    private var stacheClearedBeforeExport = false

    override fun run(spec: CommandSpec): CommandResult {
        return when (spec.title) {
            "Listing Statamic commands" -> CommandResult(0, supportedCommands().joinToString("\n"))
            "Running migrate --pretend" -> success()
            "Inspecting database storage" -> CommandResult(0, snapshotOutput(populated = databasePopulated))
            "Backing up database state" -> {
                val backupPath = Path.of(spec.envOverrides.getValue("STATAMIC_TOOLKIT_BACKUP_PATH"))
                backupPath.parent?.let(Files::createDirectories)
                Files.writeString(backupPath, "{}")
                success("meta\tbackup_path\t${backupPath.toAbsolutePath()}\n")
            }
            "Installing the Eloquent driver" -> {
                executedCommands += "install:eloquent-driver"
                installDriverArgs = spec.args
                if ("--repositories=tokens" !in spec.args) {
                    return CommandResult(
                        1,
                        """
                        Laravel\Prompts\Exceptions\NonInteractiveValidationException

                        You must select at least one repository to migrate.
                        """.trimIndent()
                    )
                }
                writeDriverConfig("file")
                success()
            }
            "Publishing the Eloquent driver migrations" -> success()
            "Publishing the Eloquent entries migration" -> success()
            "Running project migrations" -> success()
            "Updating the driver config" -> {
                val script = spec.args.last()
                val driver = when {
                    script.contains("'driver' => 'eloquent'") -> "eloquent"
                    script.contains("'driver' => 'file'") -> "file"
                    else -> error("Unexpected driver rewrite script: $script")
                }
                writeDriverConfig(driver)
                success()
            }
            "Clearing the Statamic stache before export" -> {
                executedCommands += "stache:clear"
                stacheClearedBeforeExport = true
                success()
            }
            "Clearing the Statamic stache" -> {
                executedCommands += "stache:clear"
                success()
            }
            else -> {
                val pleaseCommand = spec.title.removePrefix("Running ").takeIf {
                    it.startsWith("eloquent:")
                } ?: spec.args.getOrNull(2)
                when {
                    pleaseCommand == null -> success()
                    pleaseCommand.startsWith("eloquent:import-") -> {
                        executedCommands += pleaseCommand
                        databasePopulated = true
                        success()
                    }
                    pleaseCommand == "eloquent:export-collections" -> {
                        executedCommands += pleaseCommand
                        if (requireStacheClearBeforeExportCollections && !stacheClearedBeforeExport) {
                            return CommandResult(1, incompleteObjectExportOutput())
                        }
                        writeExportedCollections()
                        success()
                    }
                    pleaseCommand == "eloquent:export-entries" -> {
                        executedCommands += pleaseCommand
                        writeExportedEntries()
                        success()
                    }
                    pleaseCommand.startsWith("eloquent:export-") -> {
                        executedCommands += pleaseCommand
                        success()
                    }
                    else -> success()
                }
            }
        }
    }

    private fun supportedCommands(): List<String> {
        return listOf(
            "install:eloquent-driver",
            "stache:clear",
            "eloquent:export-collections",
            "eloquent:export-entries",
            "eloquent:export-blueprints",
            "eloquent:export-forms",
            "eloquent:export-globals",
            "eloquent:export-navs",
            "eloquent:export-taxonomies",
            "eloquent:export-assets",
            "eloquent:export-sites",
        ) + listOf(
            "eloquent:import-collections",
            "eloquent:import-entries",
            "eloquent:import-blueprints",
            "eloquent:import-forms",
            "eloquent:import-globals",
            "eloquent:import-navs",
            "eloquent:import-taxonomies",
            "eloquent:import-assets",
            "eloquent:import-sites",
        )
    }

    private fun writeExportedCollections() {
        val collectionDirectory = root.resolve("content/collections/posts")
        Files.createDirectories(collectionDirectory)
        Files.writeString(root.resolve("content/collections/posts.yaml"), "title: Posts\n")
    }

    private fun writeExportedEntries() {
        val entriesDirectory = root.resolve("content/collections/posts")
        Files.createDirectories(entriesDirectory)
        Files.writeString(entriesDirectory.resolve("hello-world.md"), "---\ntitle: Hello\n---\n")
    }

    private fun incompleteObjectExportOutput(): String = """
        "/Users/portseif/Library/Application Support/Herd/bin/php" please eloquent:export-collections --no-interaction --force
         0/3 [░░░░░░░░░░░░░░░░░░░░░░░░░░░░]   0%
           Error

          The script tried to call a method on an incomplete object. Please ensure that the class definition "Statamic\Eloquent\Collections\Collection" of the object you are trying to operate on was loaded _before_ unserialize() gets called or provide an autoloader to load the class definition

          at vendor/statamic/cms/src/Stache/Stores/BasicStore.php:27
             23▕             return null;
             24▕         }
             25▕
             26▕         if (${'$'}item = ${'$'}this->getCachedItem(${'$'}key)) {
          ➜  27▕             if (method_exists(${'$'}item, 'syncOriginal')) {
             28▕                 ${'$'}item->syncOriginal();

              +2 vendor frames

          34  please:18
              Illuminate\Foundation\Application::handleCommand(Object(Symfony\Component\Console\Input\ArgvInput))
    """.trimIndent()

    private fun snapshotOutput(populated: Boolean): String {
        return buildString {
            append("meta\tlocation\tmysql://127.0.0.1:3306/statamic\n")
            append("meta\tsize_bytes\t4096\n")
            append("meta\tavailable_bytes\t104857600\n")
            if (populated) {
                append("metric\tcollections\t1\texact\tposts\n")
                append("metric\tentries\t1\tcount\tcount-only\n")
            }
        }
    }

    fun writeDriverConfig(driver: String) {
        val configPath = root.resolve("config/statamic/eloquent-driver.php")
        Files.createDirectories(configPath.parent)
        Files.writeString(
            configPath,
            """
            <?php

            return [
                'collections' => [
                    'driver' => '$driver',
                ],
                'entries' => [
                    'driver' => '$driver',
                ],
                'forms' => [
                    'driver' => '$driver',
                ],
            ];
            """.trimIndent() + "\n"
        )
    }

    private fun success(output: String = ""): CommandResult = CommandResult(
        exitCode = 0,
        output = output,
    )
}
