package com.antlers.support.statamic

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines
import kotlin.io.path.writeText

enum class StorageConversionTarget(val driver: StatamicDriver, val displayName: String) {
    DATABASE(StatamicDriver.ELOQUENT, "database"),
    FLAT_FILE(StatamicDriver.FLAT_FILE, "flat file")
}

enum class StorageConflictResolution {
    MERGE,
    OVERWRITE,
    CANCEL
}

enum class StorageComparisonMode {
    EXACT_IDENTIFIERS,
    COUNT_ONLY
}

enum class StorageConversionPhase {
    ANALYZING,
    VALIDATING,
    BACKUP,
    MIGRATING,
    VERIFYING,
    FINALIZING,
    ROLLBACK,
    COMPLETED
}

data class DatabaseConnectionConfig(
    val connection: String,
    val host: String,
    val port: String,
    val database: String,
    val username: String,
    val password: String,
) {
    fun toEnvironment(): Map<String, String> = mapOf(
        "DB_CONNECTION" to connection,
        "DB_HOST" to host,
        "DB_PORT" to port,
        "DB_DATABASE" to database,
        "DB_USERNAME" to username,
        "DB_PASSWORD" to password,
    )

    fun locationDescription(): String {
        return if (connection == "sqlite") {
            "sqlite:$database"
        } else {
            "$connection://$host:$port/$database"
        }
    }
}

data class StorageMetric(
    val key: String,
    val label: String,
    val count: Int,
    val comparisonMode: StorageComparisonMode,
    val identifiers: List<String> = emptyList(),
) {
    val hash: String = hashMetricIdentifiers(count, identifiers)
}

data class StorageSnapshot(
    val driver: StatamicDriver,
    val locationDescription: String,
    val sizeBytes: Long,
    val availableSpaceBytes: Long?,
    val metrics: Map<String, StorageMetric>,
    val warnings: List<String> = emptyList(),
    val capturedAtMillis: Long = System.currentTimeMillis(),
) {
    val totalRecords: Int
        get() = metrics.values.sumOf(StorageMetric::count)
}

data class StorageValidationIssue(
    val message: String,
    val fatal: Boolean = true,
)

data class StorageConversionAnalysis(
    val request: StorageConversionRequest,
    val source: StorageSnapshot,
    val target: StorageSnapshot,
    val warnings: List<String>,
    val errors: List<String>,
    val supportedResolutions: List<StorageConflictResolution>,
    val commandPreview: List<String>,
    val backupRoot: Path,
) {
    val hasConflict: Boolean
        get() = target.totalRecords > 0
}

data class StorageConversionRequest(
    val target: StorageConversionTarget,
    val databaseConfig: DatabaseConnectionConfig? = null,
    val conflictResolution: StorageConflictResolution = StorageConflictResolution.MERGE,
)

data class StorageConversionProgress(
    val phase: StorageConversionPhase,
    val percent: Int,
    val currentOperation: String,
    val detail: String = "",
    val eta: String? = null,
)

data class StorageConversionResult(
    val success: Boolean,
    val title: String,
    val message: String,
    val backupDirectory: Path?,
    val logFile: Path?,
    val warnings: List<String>,
    val verificationMessages: List<String>,
    val rollbackPerformed: Boolean,
)

private open class StorageConversionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
private class StorageConversionValidationException(message: String) : StorageConversionException(message)
private class StorageConversionCommandException(
    message: String,
    val commandLine: String,
    val output: String,
) : StorageConversionException(message)

private class StorageConversionRollbackException(message: String, cause: Throwable? = null) : StorageConversionException(message, cause)

@Service(Service.Level.PROJECT)
class StatamicStorageConversionService(private val project: Project) {
    private val logger = Logger.getInstance(StatamicStorageConversionService::class.java)

    @Volatile
    private var currentOverviewCache: Pair<StatamicDriver, StorageSnapshot>? = null

    fun currentStorageOverview(forceRefresh: Boolean = false): StorageSnapshot? {
        val basePath = project.basePath ?: return null
        val driver = detectStatamicDriver(basePath)
        val cached = currentOverviewCache
        if (!forceRefresh && cached != null) {
            val age = System.currentTimeMillis() - cached.second.capturedAtMillis
            if (cached.first == driver && age < OVERVIEW_CACHE_TTL_MS) {
                return cached.second
            }
        }

        val engine = StorageConversionEngine(
            basePath = Paths.get(basePath),
            phpPath = StatamicProjectCollections.getInstance(project).findPhpPath(),
            logger = logger,
        )
        return try {
            val snapshot = engine.captureSnapshot(
                driver = driver,
                databaseConfig = null,
            )
            currentOverviewCache = driver to snapshot
            snapshot
        } catch (t: Throwable) {
            logger.warn("Unable to compute current storage overview", t)
            cached?.second
        }
    }

    fun analyze(request: StorageConversionRequest): StorageConversionAnalysis {
        val basePath = project.basePath ?: throw StorageConversionValidationException("This project does not have a base path.")
        val engine = StorageConversionEngine(
            basePath = Paths.get(basePath),
            phpPath = StatamicProjectCollections.getInstance(project).findPhpPath(),
            logger = logger,
        )
        return engine.analyze(request)
    }

    fun convert(
        request: StorageConversionRequest,
        onProgress: (StorageConversionProgress) -> Unit,
    ): StorageConversionResult {
        val basePath = project.basePath ?: throw StorageConversionValidationException("This project does not have a base path.")
        val engine = StorageConversionEngine(
            basePath = Paths.get(basePath),
            phpPath = StatamicProjectCollections.getInstance(project).findPhpPath(),
            logger = logger,
        )
        val result = engine.convert(request, onProgress)
        currentOverviewCache = null
        return result
    }

    companion object {
        fun getInstance(project: Project): StatamicStorageConversionService =
            project.getService(StatamicStorageConversionService::class.java)

        private const val OVERVIEW_CACHE_TTL_MS = 10_000L
    }
}

internal class StorageConversionEngine(
    private val basePath: Path,
    private val phpPath: String?,
    private val logger: Logger,
    private val commandExecutor: CommandExecutor = IdeCommandExecutor(),
) {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
    private val backupRootBase = basePath.resolve("storage/statamic-toolkit/backups")
    private val logRoot = basePath.resolve("storage/logs")
    private val lockFile = basePath.resolve("storage/statamic-toolkit/conversion.lock")

    fun analyze(request: StorageConversionRequest): StorageConversionAnalysis {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val sourceDriver = detectStatamicDriver(basePath.toString())
        if (sourceDriver == request.target.driver) {
            errors += "The project is already using ${request.target.displayName} storage."
        }

        val source = captureSnapshot(sourceDriver, request.databaseConfig)
        val target = when (request.target) {
            StorageConversionTarget.DATABASE -> captureDatabaseSnapshot(request.databaseConfig, warnings)
            StorageConversionTarget.FLAT_FILE -> captureFlatFileSnapshot()
        }

        validateCommonEnvironment(source, target, request, warnings, errors)

        if (request.target == StorageConversionTarget.FLAT_FILE && sourceDriver == StatamicDriver.ELOQUENT) {
            val orphanCount = countOrphanedEntries()
            if (orphanCount > 0) {
                warnings += "Found $orphanCount orphaned ${if (orphanCount == 1) "entry" else "entries"} in the database referencing non-existent collections. These will be removed before export to prevent failures."
            }
        }

        val resolutions = supportedResolutions(request.target, target)
        val preview = buildCommandPreview(request.target)

        return StorageConversionAnalysis(
            request = request,
            source = source,
            target = target,
            warnings = warnings.distinct(),
            errors = errors.distinct(),
            supportedResolutions = resolutions,
            commandPreview = preview,
            backupRoot = backupRootBase,
        )
    }

    fun convert(
        request: StorageConversionRequest,
        onProgress: (StorageConversionProgress) -> Unit,
    ): StorageConversionResult {
        val sessionTimestamp = timestampFormatter.format(Instant.now())
        val backupDirectory = backupRootBase.resolve("$sessionTimestamp-${request.target.name.lowercase()}")
        val logFile = logRoot.resolve("statamic-storage-conversion-$sessionTimestamp.log")
        val audit = ConversionAudit(logFile, logger)
        var rollbackPerformed = false
        var analysis: StorageConversionAnalysis? = null
        var databaseBackup: Path? = null
        var filesBackedUp = false
        val startedAt = System.currentTimeMillis()

        fun progress(
            phase: StorageConversionPhase,
            percent: Int,
            currentOperation: String,
            detail: String = "",
        ) {
            val eta = estimatedEta(startedAt, percent)
            onProgress(
                StorageConversionProgress(
                    phase = phase,
                    percent = percent.coerceIn(0, 100),
                    currentOperation = currentOperation,
                    detail = detail,
                    eta = eta,
                )
            )
        }

        try {
            progress(StorageConversionPhase.ANALYZING, 4, "Inspecting current storage")
            analysis = analyze(request)
            if (analysis.errors.isNotEmpty()) {
                throw StorageConversionValidationException(analysis.errors.joinToString("\n"))
            }

            progress(StorageConversionPhase.VALIDATING, 12, "Running pre-conversion checks")
            audit.info("Starting ${request.target.displayName} conversion")
            audit.info("Source driver: ${analysis.source.driver.displayName()}")
            audit.info("Target driver: ${request.target.driver.displayName()}")
            if (analysis.warnings.isNotEmpty()) {
                analysis.warnings.forEach(audit::warn)
            }

            val envOverrides = request.databaseConfig?.toEnvironment().orEmpty()
            val lock = acquireLock()
            try {
                progress(StorageConversionPhase.BACKUP, 20, "Creating project backups")
                backupDirectory.createDirectories()
                backupFileState(backupDirectory, audit)
                filesBackedUp = true

                if (request.target == StorageConversionTarget.DATABASE || analysis.source.driver == StatamicDriver.ELOQUENT) {
                    databaseBackup = backupDirectory.resolve("database-state.json")
                    backupDatabaseState(databaseBackup!!, envOverrides, audit)
                }

                if (request.target == StorageConversionTarget.FLAT_FILE &&
                    request.conflictResolution == StorageConflictResolution.OVERWRITE &&
                    analysis.target.totalRecords > 0
                ) {
                    clearFlatFileTargets(audit)
                }

                if (request.target == StorageConversionTarget.DATABASE &&
                    request.conflictResolution == StorageConflictResolution.OVERWRITE &&
                    analysis.target.totalRecords > 0
                ) {
                    clearDatabaseState(envOverrides, audit)
                }

                progress(StorageConversionPhase.MIGRATING, 35, "Running storage migration")
                when (request.target) {
                    StorageConversionTarget.DATABASE -> runDatabaseConversion(envOverrides, audit, ::progress)
                    StorageConversionTarget.FLAT_FILE -> runFlatFileConversion(audit, ::progress)
                }

                progress(StorageConversionPhase.VERIFYING, 80, "Verifying migrated data")
                val postSnapshot = captureSnapshot(request.target.driver, request.databaseConfig)
                val verificationMessages = verifyStorageTransfer(
                    source = analysis.source,
                    targetBefore = analysis.target,
                    targetAfter = postSnapshot,
                    resolution = request.conflictResolution,
                )

                progress(StorageConversionPhase.FINALIZING, 90, "Activating ${request.target.displayName} storage")
                when (request.target) {
                    StorageConversionTarget.DATABASE -> {
                        persistDatabaseConfig(request.databaseConfig ?: throw StorageConversionValidationException(
                            "Database details are required to switch to database storage."
                        ))
                        rewriteDriverConfig("eloquent")
                    }
                    StorageConversionTarget.FLAT_FILE -> rewriteDriverConfig("file")
                }
                clearStache(request.databaseConfig?.toEnvironment().orEmpty(), audit)

                progress(StorageConversionPhase.COMPLETED, 100, "Conversion complete")
                audit.info("Conversion completed successfully")

                return StorageConversionResult(
                    success = true,
                    title = "Conversion completed",
                    message = buildSuccessMessage(request, backupDirectory, logFile, verificationMessages),
                    backupDirectory = backupDirectory,
                    logFile = logFile,
                    warnings = analysis.warnings,
                    verificationMessages = verificationMessages,
                    rollbackPerformed = false,
                )
            } finally {
                lock.close()
            }
        } catch (t: Throwable) {
            logger.warn("Storage conversion failed", t)
            audit.error("Conversion failed: ${t.message}", t)
            if (t is StorageConversionCommandException) {
                audit.error("Failed command: ${t.commandLine}")
                if (t.output.isNotBlank()) {
                    audit.error("Command output:\n${t.output.trim()}")
                }
            }

            val rollbackMessages = mutableListOf<String>()
            try {
                progress(StorageConversionPhase.ROLLBACK, 92, "Restoring previous state")
                if (filesBackedUp) {
                    restoreFileState(backupDirectory, audit)
                    rollbackMessages += "Restored flat-file backup."
                }
                if (databaseBackup != null) {
                    restoreDatabaseState(databaseBackup!!, request.databaseConfig?.toEnvironment().orEmpty(), audit)
                    rollbackMessages += "Restored database backup."
                }
                rollbackPerformed = rollbackMessages.isNotEmpty()
            } catch (rollbackError: Throwable) {
                audit.error("Rollback failed: ${rollbackError.message}", rollbackError)
                throw StorageConversionRollbackException(
                    buildRollbackFailureMessage(t, rollbackError, backupDirectory, logFile),
                    rollbackError
                )
            }

            return StorageConversionResult(
                success = false,
                title = "Conversion failed",
                message = buildFailureMessage(t, backupDirectory, logFile, rollbackMessages),
                backupDirectory = backupDirectory,
                logFile = logFile,
                warnings = analysis?.warnings.orEmpty(),
                verificationMessages = rollbackMessages,
                rollbackPerformed = rollbackPerformed,
            )
        } finally {
            audit.close()
        }
    }

    fun captureSnapshot(
        driver: StatamicDriver,
        databaseConfig: DatabaseConnectionConfig?,
    ): StorageSnapshot {
        return when (driver) {
            StatamicDriver.ELOQUENT -> captureDatabaseSnapshot(databaseConfig, mutableListOf())
            StatamicDriver.FLAT_FILE, StatamicDriver.UNKNOWN -> captureFlatFileSnapshot()
        }
    }

    private fun validateCommonEnvironment(
        source: StorageSnapshot,
        target: StorageSnapshot,
        request: StorageConversionRequest,
        warnings: MutableList<String>,
        errors: MutableList<String>,
    ) {
        val php = phpPath
        if (php.isNullOrBlank()) {
            errors += "PHP could not be found. Configure PHP locally before running a storage conversion."
        }

        val artisan = basePath.resolve("artisan")
        val please = basePath.resolve("please")
        if (!artisan.exists()) {
            errors += "The project is missing an artisan file at ${artisan.invariantSeparatorsPathString}."
        }
        if (!please.exists()) {
            errors += "The project is missing a please file at ${please.invariantSeparatorsPathString}."
        }

        val envFile = basePath.resolve(".env")
        if (request.target == StorageConversionTarget.DATABASE && !Files.isWritable(envFile)) {
            errors += "The .env file is not writable, so the plugin cannot persist the database connection."
        }

        val configDir = basePath.resolve("config/statamic")
        if (!configDir.exists() || !Files.isWritable(configDir)) {
            errors += "The config/statamic directory must be writable so the storage driver can be updated."
        }

        val usableSpace = try {
            Files.getFileStore(basePath).usableSpace
        } catch (_: IOException) {
            null
        }
        val estimatedRequired = source.sizeBytes + target.sizeBytes + MINIMUM_CONVERSION_HEADROOM_BYTES
        if (usableSpace != null && usableSpace < estimatedRequired) {
            errors += "Only ${formatStorageSize(usableSpace)} is available on disk, but the conversion needs about ${formatStorageSize(estimatedRequired)} for backups and verification."
        }

        if (request.target == StorageConversionTarget.DATABASE && request.databaseConfig == null) {
            errors += "Database settings are required before switching to database storage."
        }

        if (request.conflictResolution == StorageConflictResolution.CANCEL && target.totalRecords > 0) {
            errors += "The target ${request.target.displayName} already contains data. Choose Merge or Overwrite to continue."
        }

        val commandAvailability = availablePleaseCommands(request.databaseConfig?.toEnvironment().orEmpty())
        when (request.target) {
            StorageConversionTarget.DATABASE -> {
                if (!commandAvailability.contains("install:eloquent-driver")) {
                    errors += "The `install:eloquent-driver` command is not available in this project."
                }
            }
            StorageConversionTarget.FLAT_FILE -> {
                REQUIRED_EXPORT_COMMANDS
                    .filterNot(commandAvailability::contains)
                    .forEach { errors += "The `$it` command is not available in this project." }
            }
        }

        val mapToColumnsEnabled = basePath.resolve("config/statamic/eloquent-driver.php")
            .takeIf(Path::exists)
            ?.let { file ->
                try {
                    file.readLines().any { line ->
                        line.contains("map_data_to_columns") && line.contains("true")
                    }
                } catch (_: IOException) {
                    false
                }
            } == true
        if (mapToColumnsEnabled) {
            warnings += "This project has `map_data_to_columns` enabled. Review custom entry models after conversion because non-default columns may need a manual pass."
        }

        if (request.target == StorageConversionTarget.DATABASE && request.databaseConfig != null) {
            runMigrationDryRun(request.databaseConfig.toEnvironment(), warnings, errors)
        }
    }

    private fun availablePleaseCommands(envOverrides: Map<String, String>): Set<String> {
        val php = phpPath ?: return emptySet()
        return try {
            val output = runCommand(
                title = "Listing Statamic commands",
                args = listOf(php, "please", "list", "--raw"),
                envOverrides = envOverrides,
            )
            output.lines()
                .map { line -> line.trim().substringBefore(' ') }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (t: Throwable) {
            logger.warn("Unable to list Statamic commands", t)
            emptySet()
        }
    }

    private fun countOrphanedEntries(): Int {
        val php = phpPath ?: return 0
        return try {
            val output = runCommand(
                title = "Checking for orphaned entries",
                args = listOf(php, "artisan", "tinker", "--execute", oneLine(orphanDetectionScript())),
            )
            output.lines()
                .firstOrNull { it.startsWith("orphaned_entries\t") }
                ?.substringAfter("orphaned_entries\t")
                ?.trim()
                ?.toIntOrNull() ?: 0
        } catch (t: Throwable) {
            logger.warn("Unable to check for orphaned entries", t)
            0
        }
    }

    private fun cleanOrphanedEntries(audit: ConversionAudit): Int {
        val php = phpPath ?: return 0
        val output = runCommand(
            title = "Removing orphaned entries",
            args = listOf(php, "artisan", "tinker", "--execute", oneLine(orphanCleanupScript())),
        )
        val deleted = output.lines()
            .firstOrNull { it.startsWith("deleted\t") }
            ?.substringAfter("deleted\t")
            ?.trim()
            ?.toIntOrNull() ?: 0
        if (deleted > 0) {
            audit.warn("Removed $deleted orphaned ${if (deleted == 1) "entry" else "entries"} referencing non-existent collections")
        }
        return deleted
    }

    private fun runMigrationDryRun(
        envOverrides: Map<String, String>,
        warnings: MutableList<String>,
        errors: MutableList<String>,
    ) {
        val php = phpPath ?: return
        try {
            runCommand(
                title = "Running migrate --pretend",
                args = listOf(php, "artisan", "migrate", "--pretend", "--no-interaction"),
                envOverrides = envOverrides,
            )
        } catch (t: Throwable) {
            errors += "Database validation failed while running `php artisan migrate --pretend`: ${t.message.orEmpty().trim()}"
        }

        try {
            val snapshot = captureDatabaseSnapshot(
                databaseConfig = DatabaseConnectionConfig(
                    connection = envOverrides["DB_CONNECTION"].orEmpty(),
                    host = envOverrides["DB_HOST"].orEmpty(),
                    port = envOverrides["DB_PORT"].orEmpty(),
                    database = envOverrides["DB_DATABASE"].orEmpty(),
                    username = envOverrides["DB_USERNAME"].orEmpty(),
                    password = envOverrides["DB_PASSWORD"].orEmpty(),
                ),
                warnings = warnings,
            )
            if (snapshot.availableSpaceBytes != null && snapshot.availableSpaceBytes < MINIMUM_DATABASE_HEADROOM_BYTES) {
                warnings += "The configured database reports less than ${formatStorageSize(MINIMUM_DATABASE_HEADROOM_BYTES)} of remaining capacity."
            }
        } catch (t: Throwable) {
            errors += "Database connectivity validation failed: ${t.message.orEmpty().trim()}"
        }
    }

    private fun supportedResolutions(
        target: StorageConversionTarget,
        targetSnapshot: StorageSnapshot,
    ): List<StorageConflictResolution> {
        if (targetSnapshot.totalRecords == 0) {
            return listOf(StorageConflictResolution.MERGE, StorageConflictResolution.OVERWRITE, StorageConflictResolution.CANCEL)
        }

        return when (target) {
            StorageConversionTarget.DATABASE,
            StorageConversionTarget.FLAT_FILE,
            -> listOf(StorageConflictResolution.MERGE, StorageConflictResolution.OVERWRITE, StorageConflictResolution.CANCEL)
        }
    }

    private fun buildCommandPreview(target: StorageConversionTarget): List<String> {
        return when (target) {
            StorageConversionTarget.DATABASE -> listOf(
                "Validate the target database connection and pending migrations.",
                "Back up flat-file content, the current driver config, and the existing database target.",
                "Install and migrate the Statamic Eloquent driver.",
                "Import collections, entries, blueprints, forms, globals, navigation data, taxonomies, assets, and sites.",
                "Verify record counts and restore the previous state automatically if anything fails.",
            )
            StorageConversionTarget.FLAT_FILE -> listOf(
                "Validate the active Eloquent driver and the writable flat-file target paths.",
                "Back up the current flat-file target before exporting database content.",
                "Export collections, entries, blueprints, forms, globals, navigation data, taxonomies, assets, and sites.",
                "Verify the exported file set before switching the active storage driver back to flat file.",
                "Restore the previous files automatically if anything fails.",
            )
        }
    }

    private fun captureFlatFileSnapshot(): StorageSnapshot = buildFlatFileSnapshot(basePath)

    private fun captureDatabaseSnapshot(
        databaseConfig: DatabaseConnectionConfig?,
        warnings: MutableList<String>,
    ): StorageSnapshot {
        val php = phpPath ?: throw StorageConversionValidationException("PHP could not be found.")
        val env = databaseConfig?.toEnvironment().orEmpty()
        val output = runCommand(
            title = "Inspecting database storage",
            args = listOf(php, "artisan", "tinker", "--execute", oneLine(databaseSnapshotScript())),
            envOverrides = env,
        )
        val parsed = parseDatabaseSnapshot(output)
        warnings += parsed.second

        val sizeBytes = parsed.first["size_bytes"]?.toLongOrNull() ?: 0L
        val availableBytes = parsed.first["available_bytes"]?.toLongOrNull()
        val location = parsed.first["location"].orEmpty().ifBlank {
            databaseConfig?.locationDescription() ?: "Configured database connection"
        }

        return StorageSnapshot(
            driver = StatamicDriver.ELOQUENT,
            locationDescription = location,
            sizeBytes = sizeBytes,
            availableSpaceBytes = availableBytes,
            metrics = parsed.third,
            warnings = warnings.distinct(),
        )
    }

    private fun parseDatabaseSnapshot(output: String): Triple<Map<String, String>, List<String>, Map<String, StorageMetric>> {
        val meta = mutableMapOf<String, String>()
        val warnings = mutableListOf<String>()
        val metrics = linkedMapOf<String, StorageMetric>()

        output.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                val parts = line.split('\t', limit = 6)
                when (parts.firstOrNull()) {
                    "meta" -> if (parts.size >= 3) meta[parts[1]] = parts[2]
                    "warning" -> if (parts.size >= 2) warnings += parts[1]
                    "metric" -> if (parts.size >= 5) {
                        val ids = if (parts[4].isBlank()) {
                            emptyList()
                        } else {
                            parts[4].split(IDENTIFIER_SEPARATOR)
                        }
                        metrics[parts[1]] = StorageMetric(
                            key = parts[1],
                            label = metricLabel(parts[1]),
                            count = parts[2].toInt(),
                            comparisonMode = if (parts[3] == "exact") {
                                StorageComparisonMode.EXACT_IDENTIFIERS
                            } else {
                                StorageComparisonMode.COUNT_ONLY
                            },
                            identifiers = ids,
                        )
                    }
                }
            }

        return Triple(meta, warnings, metrics)
    }

    private fun backupFileState(
        backupDirectory: Path,
        audit: ConversionAudit,
    ) {
        for (relative in (FLAT_FILE_BACKUP_ROOTS + FILES_TO_RESTORE)) {
            val source = basePath.resolve(relative)
            if (!source.exists()) continue
            val target = backupDirectory.resolve("files").resolve(relative)
            copyPath(source, target)
            audit.info("Backed up $relative")
        }
    }

    private fun restoreFileState(
        backupDirectory: Path,
        audit: ConversionAudit,
    ) {
        for (relative in (FLAT_FILE_BACKUP_ROOTS + FILES_TO_RESTORE)) {
            val target = basePath.resolve(relative)
            deletePath(target)

            val source = backupDirectory.resolve("files").resolve(relative)
            if (source.exists()) {
                copyPath(source, target)
                audit.info("Restored $relative")
            }
        }
    }

    private fun backupDatabaseState(
        backupFile: Path,
        envOverrides: Map<String, String>,
        audit: ConversionAudit,
    ) {
        val php = phpPath ?: throw StorageConversionValidationException("PHP could not be found.")
        backupFile.parent.createDirectories()
        runCommand(
            title = "Backing up database state",
            args = listOf(
                php,
                "artisan",
                "tinker",
                "--execute",
                oneLine(databaseBackupScript()),
            ),
            envOverrides = envOverrides + mapOf(
                "STATAMIC_TOOLKIT_BACKUP_PATH" to backupFile.toAbsolutePath().invariantSeparatorsPathString
            ),
        )
        audit.info("Backed up database state to ${backupFile.invariantSeparatorsPathString}")
    }

    private fun restoreDatabaseState(
        backupFile: Path,
        envOverrides: Map<String, String>,
        audit: ConversionAudit,
    ) {
        if (!backupFile.exists()) return
        val php = phpPath ?: throw StorageConversionRollbackException("PHP could not be found for database rollback.")
        runCommand(
            title = "Restoring database state",
            args = listOf(
                php,
                "artisan",
                "tinker",
                "--execute",
                oneLine(databaseRestoreScript()),
            ),
            envOverrides = envOverrides + mapOf(
                "STATAMIC_TOOLKIT_BACKUP_PATH" to backupFile.toAbsolutePath().invariantSeparatorsPathString
            ),
        )
        audit.info("Restored database state from ${backupFile.invariantSeparatorsPathString}")
    }

    private fun clearDatabaseState(
        envOverrides: Map<String, String>,
        audit: ConversionAudit,
    ) {
        val php = phpPath ?: throw StorageConversionValidationException("PHP could not be found.")
        runCommand(
            title = "Clearing existing database target",
            args = listOf(
                php,
                "artisan",
                "tinker",
                "--execute",
                oneLine(clearDatabaseScript())
            ),
            envOverrides = envOverrides,
        )
        audit.info("Cleared the existing Eloquent target tables before import")
    }

    private fun clearFlatFileTargets(audit: ConversionAudit) {
        for (relative in FLAT_FILE_BACKUP_ROOTS) {
            val target = basePath.resolve(relative)
            if (!target.exists()) continue
            deletePath(target)
            audit.info("Cleared $relative before export")
        }
    }

    private fun runDatabaseConversion(
        envOverrides: Map<String, String>,
        audit: ConversionAudit,
        progress: (StorageConversionPhase, Int, String, String) -> Unit,
    ) {
        val php = phpPath ?: throw StorageConversionValidationException("PHP could not be found.")
        progress(StorageConversionPhase.MIGRATING, 38, "Installing the Eloquent driver", "")
        runCommand(
            title = "Installing the Eloquent driver",
            args = listOf(php, "please", "install:eloquent-driver", "--no-interaction"),
            envOverrides = envOverrides,
        )
        audit.info("Ran install:eloquent-driver")

        progress(StorageConversionPhase.MIGRATING, 48, "Running migrations", "")
        runCommand(
            title = "Running project migrations",
            args = listOf(php, "artisan", "migrate", "--no-interaction"),
            envOverrides = envOverrides,
        )
        audit.info("Ran artisan migrate")

        progress(StorageConversionPhase.MIGRATING, 58, "Preparing the driver config", "")
        ensureDriverConfigExists()

        val availableCommands = availablePleaseCommands(envOverrides)
        val importCommands = buildImportCommands(availableCommands)
        if (importCommands.isEmpty()) {
            throw StorageConversionValidationException("No `eloquent:import-*` commands are available after installing the Eloquent driver.")
        }

        val importProgressStart = 60
        val importProgressSpan = 16
        importCommands.forEachIndexed { index, command ->
            val percent = importProgressStart + ((index + 1) * importProgressSpan / importCommands.size)
            progress(
                StorageConversionPhase.MIGRATING,
                percent,
                "Importing ${command.substringAfter("eloquent:import-").replace('-', ' ')}",
                command
            )
            try {
                runCommand(
                    title = "Running $command",
                    args = buildList {
                        add(php)
                        add("please")
                        add(command)
                        add("--no-interaction")
                        if (commandSupportsForce(command)) add("--force")
                    },
                    envOverrides = envOverrides,
                )
                audit.info("Ran $command")
            } catch (t: StorageConversionCommandException) {
                if (command in CRITICAL_IMPORT_COMMANDS) throw t
                audit.warn("Skipped $command: ${t.message?.lineSequence()?.firstOrNull().orEmpty()}")
            }
        }
    }

    private fun runFlatFileConversion(
        audit: ConversionAudit,
        progress: (StorageConversionPhase, Int, String, String) -> Unit,
    ) {
        val php = phpPath ?: throw StorageConversionValidationException("PHP could not be found.")
        val availableCommands = availablePleaseCommands(emptyMap())
        val exportCommands = buildExportCommands(availableCommands)
        if (exportCommands.isEmpty()) {
            throw StorageConversionValidationException("No `eloquent:export-*` commands are available in this project.")
        }

        progress(StorageConversionPhase.MIGRATING, 40, "Checking for orphaned entries", "")
        cleanOrphanedEntries(audit)

        val progressStart = 42
        val span = 32
        exportCommands.forEachIndexed { index, command ->
            val percent = progressStart + ((index + 1) * span / exportCommands.size)
            progress(
                StorageConversionPhase.MIGRATING,
                percent,
                "Exporting ${command.substringAfter("eloquent:export-").replace('-', ' ')}",
                command
            )
            try {
                runCommand(
                    title = "Running $command",
                    args = buildList {
                        add(php)
                        add("please")
                        add(command)
                        add("--no-interaction")
                        if (commandSupportsForce(command)) add("--force")
                    },
                    envOverrides = emptyMap(),
                )
                audit.info("Ran $command")
            } catch (t: StorageConversionCommandException) {
                if (command in CRITICAL_EXPORT_COMMANDS) throw t
                audit.warn("Skipped $command: ${t.message?.lineSequence()?.firstOrNull().orEmpty()}")
            }
        }
    }

    private fun persistDatabaseConfig(config: DatabaseConnectionConfig) {
        val envFile = basePath.resolve(".env")
        val content = if (envFile.exists()) envFile.readLines().joinToString("\n") else ""
        val next = rewriteEnvContent(content, config.toEnvironment())
        envFile.writeText(next)
    }

    private fun ensureDriverConfigExists() {
        val configFile = basePath.resolve("config/statamic/eloquent-driver.php")
        if (!configFile.exists()) {
            throw StorageConversionValidationException(
                "The Eloquent driver config file was not published. `install:eloquent-driver` must create config/statamic/eloquent-driver.php."
            )
        }
    }

    private fun rewriteDriverConfig(targetDriverValue: String) {
        val php = phpPath ?: throw StorageConversionValidationException("PHP could not be found.")
        ensureDriverConfigExists()
        runCommand(
            title = "Updating the driver config",
            args = listOf(
                php,
                "artisan",
                "tinker",
                "--execute",
                oneLine(driverRewriteScript(targetDriverValue)),
            ),
        )
    }

    private fun clearStache(
        envOverrides: Map<String, String>,
        audit: ConversionAudit,
    ) {
        val php = phpPath ?: return
        try {
            runCommand(
                title = "Clearing the Statamic stache",
                args = listOf(php, "please", "stache:clear", "--no-interaction"),
                envOverrides = envOverrides,
            )
            audit.info("Cleared the Statamic stache")
        } catch (t: Throwable) {
            audit.warn("Unable to clear the Statamic stache automatically: ${t.message.orEmpty().trim()}")
        }
    }

    private fun acquireLock(): AutoCloseable {
        lockFile.parent.createDirectories()
        val channel = FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val lock = channel.tryLock()
            ?: throw StorageConversionValidationException("Another storage conversion is already running for this project.")
        return AutoCloseable {
            releaseLock(channel, lock)
        }
    }

    private fun releaseLock(channel: FileChannel, lock: FileLock) {
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    private fun runCommand(
        title: String,
        args: List<String>,
        envOverrides: Map<String, String> = emptyMap(),
    ): String {
        val result = commandExecutor.run(
            CommandSpec(
                title = title,
                args = args,
                workDir = basePath,
                envOverrides = envOverrides,
            )
        )
        if (result.exitCode != 0) {
            val commandLine = renderCommand(args)
            val errorSummary = buildString {
                append(extractCommandError(result.output))
                suggestCommandErrorHint(result.output)?.let { hint ->
                    append(" ")
                    append(hint)
                }
            }
            throw StorageConversionCommandException(
                "$title failed with exit code ${result.exitCode}: $errorSummary",
                commandLine = commandLine,
                output = result.output,
            )
        }
        return result.output
    }

    private fun databaseSnapshotScript(): String = php("""
        use Illuminate\Support\Facades\DB;
        use Illuminate\Support\Facades\Schema;
        §prefix = (string) (config('statamic.eloquent-driver.table_prefix') ?? '');
        §tables = ['addon_settings','asset_containers','assets','blueprints','collections','collection_trees','entries','fieldsets','forms','form_submissions','global_sets','navigation_trees','navigations','revisions','sites','taxonomies','terms'];
        §exact = ['addon_settings','asset_containers','blueprints','collections','collection_trees','fieldsets','forms','global_sets','navigation_trees','navigations','sites','taxonomies'];
        §driver = DB::connection()->getDriverName();
        §location = '';
        §sizeBytes = 0;
        §availableBytes = 0;
        if (§driver === 'sqlite') {
            §path = (string) DB::connection()->getConfig('database');
            §location = "sqlite:" . §path;
            §sizeBytes = file_exists(§path) ? filesize(§path) : 0;
            §availableBytes = file_exists(§path) ? disk_free_space(dirname(§path)) : 0;
        } elseif (§driver === 'pgsql') {
            §location = §driver . '://' . DB::connection()->getConfig('host') . ':' . DB::connection()->getConfig('port') . '/' . DB::connection()->getDatabaseName();
            §row = DB::selectOne('select pg_database_size(current_database()) as size');
            §sizeBytes = (int) (§row->size ?? 0);
        } else {
            §location = §driver . '://' . DB::connection()->getConfig('host') . ':' . DB::connection()->getConfig('port') . '/' . DB::connection()->getDatabaseName();
            try {
                §row = DB::selectOne('select coalesce(sum(data_length + index_length), 0) as size from information_schema.tables where table_schema = ?', [DB::connection()->getDatabaseName()]);
                §sizeBytes = (int) (§row->size ?? 0);
            } catch (\Throwable §ignored) {}
        }
        echo "meta\tlocation\t" . §location . PHP_EOL;
        echo "meta\tsize_bytes\t" . §sizeBytes . PHP_EOL;
        echo "meta\tavailable_bytes\t" . §availableBytes . PHP_EOL;
        foreach (§tables as §name) {
            §table = §prefix . §name;
            if (!Schema::hasTable(§table)) {
                echo "metric\t" . §name . "\t0\tcount\t" . PHP_EOL;
                continue;
            }
            §columns = Schema::getColumnListing(§table);
            §count = (int) DB::table(§table)->count();
            §ids = [];
            §mode = in_array(§name, §exact, true) ? 'exact' : 'count';
            if (§mode === 'exact') {
                if (in_array('handle', §columns, true)) {
                    §ids = DB::table(§table)->orderBy('handle')->pluck('handle')->map(fn (§value) => (string) §value)->all();
                } elseif (in_array('path', §columns, true)) {
                    §ids = DB::table(§table)->orderBy('path')->pluck('path')->map(fn (§value) => (string) §value)->all();
                } elseif (in_array('slug', §columns, true) && in_array('taxonomy', §columns, true)) {
                    §ids = DB::table(§table)->orderBy('taxonomy')->orderBy('slug')->get(['taxonomy', 'slug'])->map(fn (§row) => (string) §row->taxonomy . ':' . (string) §row->slug)->all();
                } elseif (in_array('name', §columns, true)) {
                    §ids = DB::table(§table)->orderBy('name')->pluck('name')->map(fn (§value) => (string) §value)->all();
                } else {
                    §mode = 'count';
                }
            }
            echo "metric\t" . §name . "\t" . §count . "\t" . §mode . "\t" . implode('${IDENTIFIER_SEPARATOR}', §ids) . PHP_EOL;
        }
    """)

    private fun databaseBackupScript(): String = php("""
        use Illuminate\Support\Facades\DB;
        use Illuminate\Support\Facades\Schema;
        §backupPath = getenv('STATAMIC_TOOLKIT_BACKUP_PATH');
        §prefix = (string) (config('statamic.eloquent-driver.table_prefix') ?? '');
        §tables = ['addon_settings','asset_containers','assets','blueprints','collections','collection_trees','entries','fieldsets','forms','form_submissions','global_sets','global_set_variables','navigation_trees','navigations','revisions','sites','taxonomies','terms','tokens'];
        §payload = [];
        foreach (§tables as §name) {
            §table = §prefix . §name;
            if (!Schema::hasTable(§table)) continue;
            §rows = DB::table(§table)->get()->map(fn (§row) => (array) §row)->all();
            §payload[§table] = §rows;
        }
        file_put_contents(§backupPath, json_encode(§payload, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
        echo "meta\tbackup_path\t" . §backupPath . PHP_EOL;
    """)

    private fun databaseRestoreScript(): String = php("""
        use Illuminate\Support\Facades\DB;
        use Illuminate\Support\Facades\Schema;
        §backupPath = getenv('STATAMIC_TOOLKIT_BACKUP_PATH');
        §payload = json_decode(file_get_contents(§backupPath), true) ?: [];
        §driver = DB::connection()->getDriverName();
        if (§driver === 'sqlite') {
            DB::statement('PRAGMA foreign_keys = OFF');
        } elseif (§driver === 'pgsql') {
            DB::statement("SET session_replication_role = 'replica'");
        } else {
            DB::statement('SET FOREIGN_KEY_CHECKS=0');
        }
        DB::connection()->beginTransaction();
        try {
            foreach (array_reverse(array_keys(§payload)) as §table) {
                if (!Schema::hasTable(§table)) continue;
                DB::table(§table)->delete();
            }
            foreach (§payload as §table => §rows) {
                if (!Schema::hasTable(§table) || empty(§rows)) continue;
                foreach (array_chunk(§rows, 200) as §chunk) {
                    DB::table(§table)->insert(§chunk);
                }
            }
            DB::connection()->commit();
        } catch (\Throwable §e) {
            DB::connection()->rollBack();
            throw §e;
        } finally {
            if (§driver === 'sqlite') {
                DB::statement('PRAGMA foreign_keys = ON');
            } elseif (§driver === 'pgsql') {
                DB::statement("SET session_replication_role = 'origin'");
            } else {
                DB::statement('SET FOREIGN_KEY_CHECKS=1');
            }
        }
        echo "meta\trestored_from\t" . §backupPath . PHP_EOL;
    """)

    private fun clearDatabaseScript(): String = php("""
        use Illuminate\Support\Facades\DB;
        use Illuminate\Support\Facades\Schema;
        §prefix = (string) (config('statamic.eloquent-driver.table_prefix') ?? '');
        §tables = ['addon_settings','asset_containers','assets','blueprints','collections','collection_trees','entries','fieldsets','forms','form_submissions','global_sets','global_set_variables','navigation_trees','navigations','revisions','sites','taxonomies','terms','tokens'];
        §driver = DB::connection()->getDriverName();
        if (§driver === 'sqlite') {
            DB::statement('PRAGMA foreign_keys = OFF');
        } elseif (§driver === 'pgsql') {
            DB::statement("SET session_replication_role = 'replica'");
        } else {
            DB::statement('SET FOREIGN_KEY_CHECKS=0');
        }
        DB::connection()->beginTransaction();
        try {
            foreach (array_reverse(§tables) as §name) {
                §table = §prefix . §name;
                if (!Schema::hasTable(§table)) continue;
                DB::table(§table)->delete();
            }
            DB::connection()->commit();
        } catch (\Throwable §e) {
            DB::connection()->rollBack();
            throw §e;
        } finally {
            if (§driver === 'sqlite') {
                DB::statement('PRAGMA foreign_keys = ON');
            } elseif (§driver === 'pgsql') {
                DB::statement("SET session_replication_role = 'origin'");
            } else {
                DB::statement('SET FOREIGN_KEY_CHECKS=1');
            }
        }
        echo "meta\tcleared\t1" . PHP_EOL;
    """)

    private fun orphanDetectionScript(): String = php("""
        use Illuminate\Support\Facades\DB;
        use Illuminate\Support\Facades\Schema;
        use Statamic\Facades\Collection;
        §prefix = (string) (config('statamic.eloquent-driver.table_prefix') ?? '');
        §entriesTable = §prefix . 'entries';
        if (!Schema::hasTable(§entriesTable)) {
            echo "orphaned_entries\t0" . PHP_EOL;
            return;
        }
        §handles = DB::table(§entriesTable)->distinct()->pluck('collection')->all();
        §orphanHandles = [];
        foreach (§handles as §handle) {
            try {
                if (Collection::findByHandle(§handle) === null) {
                    §orphanHandles[] = §handle;
                }
            } catch (\Throwable §e) {
                §orphanHandles[] = §handle;
            }
        }
        if (empty(§orphanHandles)) {
            echo "orphaned_entries\t0" . PHP_EOL;
        } else {
            echo "orphaned_entries\t" . (int) DB::table(§entriesTable)->whereIn('collection', §orphanHandles)->count() . PHP_EOL;
        }
    """)

    private fun orphanCleanupScript(): String = php("""
        use Illuminate\Support\Facades\DB;
        use Illuminate\Support\Facades\Schema;
        use Statamic\Facades\Collection;
        §prefix = (string) (config('statamic.eloquent-driver.table_prefix') ?? '');
        §entriesTable = §prefix . 'entries';
        §deleted = 0;
        if (Schema::hasTable(§entriesTable)) {
            §handles = DB::table(§entriesTable)->distinct()->pluck('collection')->all();
            §orphanHandles = [];
            foreach (§handles as §handle) {
                try {
                    if (Collection::findByHandle(§handle) === null) {
                        §orphanHandles[] = §handle;
                    }
                } catch (\Throwable §e) {
                    §orphanHandles[] = §handle;
                }
            }
            if (!empty(§orphanHandles)) {
                §deleted = (int) DB::table(§entriesTable)->whereIn('collection', §orphanHandles)->delete();
            }
        }
        echo "deleted\t" . §deleted . PHP_EOL;
    """)

    private fun driverRewriteScript(targetDriver: String): String = php("""
        §path = config_path('statamic/eloquent-driver.php');
        §content = file_get_contents(§path);
        §content = preg_replace("/'driver'\\s*=>\\s*'[^']+'/", "'driver' => '$targetDriver'", §content);
        file_put_contents(§path, §content);
    """)
}

internal data class CommandSpec(
    val title: String,
    val args: List<String>,
    val workDir: Path,
    val envOverrides: Map<String, String>,
)

internal data class CommandResult(
    val exitCode: Int,
    val output: String,
)

internal interface CommandExecutor {
    fun run(spec: CommandSpec): CommandResult
}

private class IdeCommandExecutor : CommandExecutor {
    override fun run(spec: CommandSpec): CommandResult {
        val commandLine = GeneralCommandLine()
            .withExePath(spec.args.first())
            .withParameters(spec.args.drop(1))
            .withWorkDirectory(spec.workDir.toFile())

        spec.envOverrides.forEach { (key, value) ->
            commandLine.withEnvironment(key, value)
        }

        val handler = OSProcessHandler(commandLine)
        val output = StringBuilder()
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                output.append(event.text)
            }
        })
        handler.startNotify()
        handler.waitFor()
        return CommandResult(
            exitCode = handler.exitCode ?: -1,
            output = output.toString(),
        )
    }
}

private class ConversionAudit(logFile: Path, private val logger: Logger) : AutoCloseable {
    private val writer: BufferedWriter

    init {
        logFile.parent.createDirectories()
        writer = Files.newBufferedWriter(
            logFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    fun info(message: String) {
        logger.info(message)
        write("INFO", message)
    }

    fun warn(message: String) {
        logger.warn(message)
        write("WARN", message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        logger.warn(message, throwable)
        write("ERROR", message)
        throwable?.stackTraceToString()?.let { write("TRACE", it) }
    }

    private fun write(level: String, message: String) {
        writer.append(timestamp()).append(" ").append(level).append(" ").append(message).appendLine()
        writer.flush()
    }

    private fun timestamp(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())

    override fun close() {
        writer.close()
    }
}

internal fun rewriteEnvContent(currentContent: String, updates: Map<String, String>): String {
    val lines = currentContent.lines().toMutableList()
    if (lines.isEmpty()) {
        lines += ""
    }

    updates.forEach { (key, value) ->
        val entry = "$key=$value"
        val index = lines.indexOfFirst { line ->
            line.trimStart().startsWith("$key=")
        }
        if (index >= 0) {
            lines[index] = entry
        } else {
            lines += entry
        }
    }

    return lines.filterIndexed { index, line ->
        index != lines.lastIndex || line.isNotBlank()
    }.joinToString("\n").trimEnd() + "\n"
}

internal fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return DecimalFormat("#,##0.#").format(value) + " " + units[unitIndex]
}

internal fun detectStatamicDriver(basePath: String): StatamicDriver {
    val configFile = File(basePath, "config/statamic/eloquent-driver.php")
    if (!configFile.exists()) return StatamicDriver.FLAT_FILE

    return try {
        val content = configFile.readText()
        val collectionsSection = content.substringAfter("'collections'", "")
        if (collectionsSection.isNotEmpty() && collectionsSection.substringBefore("]").contains("'eloquent'")) {
            StatamicDriver.ELOQUENT
        } else {
            StatamicDriver.FLAT_FILE
        }
    } catch (_: Exception) {
        StatamicDriver.FLAT_FILE
    }
}

internal fun buildFlatFileSnapshot(basePath: Path): StorageSnapshot {
    val warnings = mutableListOf<String>()
    val metrics = linkedMapOf<String, StorageMetric>()
    val relevantPaths = mutableListOf<Path>()

    fun addMetric(metric: StorageMetric) {
        metrics[metric.key] = metric
    }

    addMetric(exactMetric("addon_settings", "Addon settings", collectRelativeFiles(basePath.resolve("content/addons"), YAML_EXTENSIONS)))
    addMetric(exactMetric("asset_containers", "Asset containers", collectTopLevelHandles(basePath.resolve("content/assets"))))
    addMetric(countMetric("assets", "Asset metadata", collectNestedFiles(basePath.resolve("content/assets"), YAML_EXTENSIONS, skipTopLevel = true).size))
    addMetric(exactMetric("blueprints", "Blueprints", collectRelativeFiles(basePath.resolve("resources/blueprints"), YAML_EXTENSIONS)))
    addMetric(exactMetric("collections", "Collections", collectRootHandles(basePath.resolve("content/collections"))))
    addMetric(exactMetric("collection_trees", "Collection trees", collectRelativeFiles(basePath.resolve("content/trees/collections"), YAML_EXTENSIONS)))
    addMetric(countMetric("entries", "Entries", countCollectionEntries(basePath.resolve("content/collections"))))
    addMetric(exactMetric("fieldsets", "Fieldsets", collectRelativeFiles(basePath.resolve("resources/fieldsets"), YAML_EXTENSIONS)))
    addMetric(exactMetric("forms", "Forms", collectTopLevelHandles(basePath.resolve("resources/forms"))))
    addMetric(countMetric("form_submissions", "Form submissions", countFilesInAny(listOf(
        basePath.resolve("storage/forms"),
        basePath.resolve("storage/form-submissions"),
    ), CONTENT_FILE_EXTENSIONS)))
    addMetric(exactMetric("global_sets", "Global sets", collectTopLevelHandles(basePath.resolve("content/globals"))))
    addMetric(exactMetric("navigations", "Navigations", collectRootHandles(basePath.resolve("content/navigation"))))
    addMetric(exactMetric("navigation_trees", "Navigation trees", collectRelativeFiles(basePath.resolve("content/trees/navigation"), YAML_EXTENSIONS)))
    addMetric(countMetric("revisions", "Revisions", countFilesInAny(listOf(basePath.resolve("storage/statamic/revisions")), CONTENT_FILE_EXTENSIONS + YAML_EXTENSIONS)))
    addMetric(exactMetric("sites", "Sites", collectRelativeFiles(basePath.resolve("resources/sites"), YAML_EXTENSIONS)))
    addMetric(exactMetric("taxonomies", "Taxonomies", collectRootHandles(basePath.resolve("content/taxonomies"))))
    addMetric(countMetric("terms", "Terms", countTaxonomyTerms(basePath.resolve("content/taxonomies"))))

    for (relative in FLAT_FILE_BACKUP_ROOTS) {
        val path = basePath.resolve(relative)
        if (path.exists()) {
            relevantPaths.add(path)
        }
    }

    val sizeBytes = relevantPaths.sumOf(::safeDirectorySize)
    val freeSpace = try {
        Files.getFileStore(basePath).usableSpace
    } catch (_: IOException) {
        null
    }

    if (metrics.values.all { it.count == 0 }) {
        warnings += "No tracked flat-file records were found in the usual Statamic content locations."
    }

    return StorageSnapshot(
        driver = StatamicDriver.FLAT_FILE,
        locationDescription = FLAT_FILE_BACKUP_ROOTS.joinToString(", "),
        sizeBytes = sizeBytes,
        availableSpaceBytes = freeSpace,
        metrics = metrics,
        warnings = warnings,
    )
}

internal fun verifyStorageTransfer(
    source: StorageSnapshot,
    targetBefore: StorageSnapshot,
    targetAfter: StorageSnapshot,
    resolution: StorageConflictResolution,
): List<String> {
    val messages = mutableListOf<String>()
    val failures = mutableListOf<String>()

    source.metrics.values.forEach { sourceMetric ->
        val targetMetric = targetAfter.metrics[sourceMetric.key]
            ?: StorageMetric(
                key = sourceMetric.key,
                label = sourceMetric.label,
                count = 0,
                comparisonMode = sourceMetric.comparisonMode,
            )
        val beforeMetric = targetBefore.metrics[sourceMetric.key]
            ?: StorageMetric(
                key = sourceMetric.key,
                label = sourceMetric.label,
                count = 0,
                comparisonMode = sourceMetric.comparisonMode,
            )

        when (sourceMetric.comparisonMode) {
            StorageComparisonMode.EXACT_IDENTIFIERS -> {
                val sourceIdentifiers = sourceMetric.identifiers.toSet()
                val targetIdentifiers = targetMetric.identifiers.toSet()
                val beforeIdentifiers = beforeMetric.identifiers.toSet()
                when (resolution) {
                    StorageConflictResolution.MERGE -> {
                        if (!targetIdentifiers.containsAll(sourceIdentifiers)) {
                            failures += "${sourceMetric.label}: some expected identifiers were not present after conversion."
                        } else {
                            messages += "${sourceMetric.label}: ${sourceMetric.count} identifier(s) verified."
                        }
                        if (targetIdentifiers.size < beforeIdentifiers.size) {
                            failures += "${sourceMetric.label}: the merge removed identifiers that existed before conversion."
                        }
                    }
                    StorageConflictResolution.OVERWRITE,
                    StorageConflictResolution.CANCEL,
                    -> {
                        if (targetIdentifiers != sourceIdentifiers) {
                            failures += "${sourceMetric.label}: expected ${sourceIdentifiers.size} identifier(s), found ${targetIdentifiers.size}."
                        } else {
                            messages += "${sourceMetric.label}: identifiers matched exactly."
                        }
                    }
                }
            }
            StorageComparisonMode.COUNT_ONLY -> {
                when (resolution) {
                    StorageConflictResolution.MERGE -> {
                        if (targetMetric.count < sourceMetric.count || targetMetric.count < beforeMetric.count) {
                            failures += "${sourceMetric.label}: expected at least ${maxOf(sourceMetric.count, beforeMetric.count)} record(s), found ${targetMetric.count}."
                        } else {
                            messages += "${sourceMetric.label}: ${targetMetric.count} record(s) present after merge."
                        }
                    }
                    StorageConflictResolution.OVERWRITE,
                    StorageConflictResolution.CANCEL,
                    -> {
                        if (targetMetric.count != sourceMetric.count) {
                            failures += "${sourceMetric.label}: expected ${sourceMetric.count} record(s), found ${targetMetric.count}."
                        } else {
                            messages += "${sourceMetric.label}: ${targetMetric.count} record(s) verified."
                        }
                    }
                }
            }
        }
    }

    return messages + failures
}

private fun hashMetricIdentifiers(count: Int, identifiers: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(count.toString().toByteArray(StandardCharsets.UTF_8))
    identifiers.sorted().forEach { identifier ->
        digest.update(identifier.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun exactMetric(key: String, label: String, identifiers: List<String>): StorageMetric {
    return StorageMetric(
        key = key,
        label = label,
        count = identifiers.size,
        comparisonMode = StorageComparisonMode.EXACT_IDENTIFIERS,
        identifiers = identifiers.sorted(),
    )
}

private fun countMetric(key: String, label: String, count: Int): StorageMetric {
    return StorageMetric(
        key = key,
        label = label,
        count = count,
        comparisonMode = StorageComparisonMode.COUNT_ONLY,
    )
}

private fun collectRelativeFiles(root: Path, extensions: Set<String>): List<String> {
    if (!root.exists()) return emptyList()
    val results = mutableListOf<String>()
    Files.walk(root).use { paths ->
        paths.filter { it.isRegularFile() }
            .forEach { file ->
                val extension = file.fileName.toString().substringAfterLast('.', "")
                if (extension.lowercase() in extensions) {
                    results += root.relativize(file).invariantSeparatorsPathString
                }
            }
    }
    return results.sorted()
}

private fun collectNestedFiles(root: Path, extensions: Set<String>, skipTopLevel: Boolean): List<String> {
    if (!root.exists()) return emptyList()
    val results = mutableListOf<String>()
    Files.walk(root).use { paths ->
        paths.filter { it.isRegularFile() }
            .forEach { file ->
                val extension = file.fileName.toString().substringAfterLast('.', "")
                if (extension.lowercase() !in extensions) return@forEach
                val relative = root.relativize(file)
                if (skipTopLevel && relative.nameCount == 1) return@forEach
                results += relative.invariantSeparatorsPathString
            }
    }
    return results.sorted()
}

private fun collectTopLevelHandles(root: Path): List<String> {
    if (!root.exists() || !root.isDirectory()) return emptyList()
    Files.list(root).use { children ->
        return children
            .filter { it.isRegularFile() && it.fileName.toString().substringAfterLast('.', "").lowercase() in YAML_EXTENSIONS }
            .map { it.nameWithoutExtension }
            .toList()
            .sorted()
    }
}

private fun collectRootHandles(root: Path): List<String> {
    if (!root.exists() || !root.isDirectory()) return emptyList()
    val handles = linkedSetOf<String>()
    Files.list(root).use { children ->
        children.forEach { child ->
            when {
                child.isDirectory() -> handles += child.name
                child.isRegularFile() && child.fileName.toString().substringAfterLast('.', "").lowercase() in YAML_EXTENSIONS ->
                    handles += child.nameWithoutExtension
            }
        }
    }
    return handles.toList().sorted()
}

private fun countCollectionEntries(root: Path): Int {
    if (!root.exists() || !root.isDirectory()) return 0
    var count = 0
    Files.list(root).use { collections ->
        collections.filter(Path::isDirectory).forEach { directory ->
            Files.walk(directory).use { paths ->
                count += paths
                    .filter { it.isRegularFile() }
                    .toList()
                    .count { path ->
                        path.fileName.toString().substringAfterLast('.', "").lowercase() in CONTENT_FILE_EXTENSIONS + YAML_EXTENSIONS
                    }
            }
        }
    }
    return count
}

private fun countTaxonomyTerms(root: Path): Int {
    if (!root.exists() || !root.isDirectory()) return 0
    var count = 0
    Files.list(root).use { taxonomies ->
        taxonomies.filter(Path::isDirectory).forEach { directory ->
            Files.walk(directory).use { paths ->
                count += paths
                    .filter { it.isRegularFile() }
                    .toList()
                    .count { file ->
                        file.fileName.toString().substringAfterLast('.', "").lowercase() in CONTENT_FILE_EXTENSIONS + YAML_EXTENSIONS
                    }
            }
        }
    }
    return count
}

private fun countFilesInAny(paths: List<Path>, extensions: Set<String>): Int {
    return paths.sumOf { path ->
        if (!path.exists()) return@sumOf 0
        Files.walk(path).use { files ->
            files.filter { it.isRegularFile() }
                .toList()
                .count { file ->
                    file.fileName.toString().substringAfterLast('.', "").lowercase() in extensions
                }
        }
    }
}

private fun safeDirectorySize(path: Path): Long {
    if (!path.exists()) return 0
    return if (path.isRegularFile()) {
        try {
            Files.size(path)
        } catch (_: IOException) {
            0
        }
    } else {
        Files.walk(path).use { files ->
            files.filter(Path::isRegularFile)
                .toList()
                .sumOf { file ->
                    try {
                        Files.size(file)
                    } catch (_: IOException) {
                        0
                    }
                }
        }
    }
}

private fun copyPath(source: Path, target: Path) {
    if (!source.exists()) return
    if (source.isRegularFile()) {
        target.parent?.createDirectories()
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
        return
    }

    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            target.resolve(source.relativize(dir).invariantSeparatorsPathString).createDirectories()
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val relative = source.relativize(file).invariantSeparatorsPathString
            val destination = target.resolve(relative)
            destination.parent?.createDirectories()
            Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            return FileVisitResult.CONTINUE
        }
    })
}

private fun deletePath(path: Path) {
    if (!path.exists()) return
    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.deleteIfExists(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            Files.deleteIfExists(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

private fun estimatedEta(startedAtMillis: Long, percent: Int): String? {
    if (percent <= 0 || percent >= 100) return null
    val elapsed = System.currentTimeMillis() - startedAtMillis
    val remaining = elapsed * (100 - percent) / percent
    return when {
        remaining < 60_000L -> "${remaining / 1_000L}s remaining"
        remaining < 3_600_000L -> "${remaining / 60_000L}m remaining"
        else -> "${remaining / 3_600_000L}h remaining"
    }
}

internal fun extractCommandError(output: String): String {
    val lines = normalizeCommandOutput(output)
    if (lines.isEmpty()) {
        return "The command exited with an error."
    }

    val errorMarker = lines.indexOfFirst { it.equals("Error", ignoreCase = true) }
    if (errorMarker >= 0) {
        lines.drop(errorMarker + 1)
            .firstOrNull(::isActionableErrorLine)
            ?.let { return it.take(220) }
    }

    lines.firstOrNull(::isActionableErrorLine)?.let { return it.take(220) }

    return lines.lastOrNull { !isNoiseLine(it) }
        ?.take(220)
        ?: "The command exited with an error."
}

internal fun suggestCommandErrorHint(output: String): String? {
    val normalized = normalizeCommandOutput(output).joinToString("\n").lowercase()
    return when {
        "entryclass() on null" in normalized ->
            "Statamic could not resolve a collection for at least one exported entry. Check for orphaned entries or missing collection handles in the source project, then retry."
        else -> null
    }
}

private fun normalizeCommandOutput(output: String): List<String> {
    return output.lines()
        .map(::stripAnsiCodes)
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot(::isNoiseLine)
}

private fun stripAnsiCodes(text: String): String {
    return text.replace(Regex("\\u001B\\[[;\\d]*m"), "")
}

private fun isNoiseLine(line: String): Boolean {
    return line.startsWith("INFO") ||
        line.startsWith("+") ||
        line.startsWith("WARN") ||
        isShellCommandEcho(line) ||
        isProgressLine(line)
}

private fun isShellCommandEcho(line: String): Boolean {
    return (line.startsWith("\"") || line.startsWith("/") || line.startsWith("\\")) &&
        (line.contains(" please ") || line.contains(" artisan "))
}

private fun isProgressLine(line: String): Boolean {
    return line.contains("[░") ||
        line.matches(Regex("""^\d+/\d+\s+\[.*$"""))
}

private fun isActionableErrorLine(line: String): Boolean {
    if (line.startsWith("at ")) return false
    if (line.matches(Regex("""^\+?\d+\s+vendor frames.*$"""))) return false
    if (line.matches(Regex("""^\d+\s+please:\d+.*$"""))) return false

    val lowercase = line.lowercase()
    return lowercase.contains("exception") ||
        lowercase.contains("error") ||
        lowercase.contains("failed") ||
        lowercase.contains("unable") ||
        lowercase.contains("cannot") ||
        lowercase.contains("call to ") ||
        lowercase.contains("undefined") ||
        lowercase.contains("not found")
}

private fun renderCommand(args: List<String>): String {
    return args.joinToString(" ") { arg ->
        if (arg.any { it.isWhitespace() || it == '"' }) {
            "\"" + arg.replace("\"", "\\\"") + "\""
        } else {
            arg
        }
    }
}

private fun metricLabel(key: String): String = when (key) {
    "addon_settings" -> "Addon settings"
    "asset_containers" -> "Asset containers"
    "assets" -> "Asset metadata"
    "blueprints" -> "Blueprints"
    "collection_trees" -> "Collection trees"
    "collections" -> "Collections"
    "entries" -> "Entries"
    "fieldsets" -> "Fieldsets"
    "forms" -> "Forms"
    "form_submissions" -> "Form submissions"
    "global_sets" -> "Global sets"
    "navigations" -> "Navigations"
    "navigation_trees" -> "Navigation trees"
    "revisions" -> "Revisions"
    "sites" -> "Sites"
    "taxonomies" -> "Taxonomies"
    "terms" -> "Terms"
    else -> key.replace('_', ' ').replaceFirstChar(Char::titlecase)
}

private fun buildImportCommands(availableCommands: Set<String>): List<String> {
    val commands = REQUIRED_IMPORT_COMMANDS.filter(availableCommands::contains).toMutableList()
    OPTIONAL_IMPORT_COMMANDS.filter(availableCommands::contains).forEach(commands::add)
    return commands
}

private val COMMANDS_WITH_FORCE = setOf(
    "eloquent:export-collections",
    "eloquent:export-forms",
    "eloquent:export-globals",
    "eloquent:export-navs",
    "eloquent:export-taxonomies",
    "eloquent:export-assets",
    "eloquent:import-collections",
    "eloquent:import-blueprints",
    "eloquent:import-forms",
    "eloquent:import-globals",
    "eloquent:import-navs",
    "eloquent:import-taxonomies",
    "eloquent:import-assets",
)

private val CRITICAL_EXPORT_COMMANDS = setOf(
    "eloquent:export-collections",
    "eloquent:export-entries",
)

private val CRITICAL_IMPORT_COMMANDS = setOf(
    "eloquent:import-collections",
    "eloquent:import-entries",
)

private fun commandSupportsForce(command: String): Boolean =
    command in COMMANDS_WITH_FORCE

private fun buildExportCommands(availableCommands: Set<String>): List<String> {
    val commands = REQUIRED_EXPORT_COMMANDS.filter(availableCommands::contains).toMutableList()
    OPTIONAL_EXPORT_COMMANDS.filter(availableCommands::contains).forEach(commands::add)
    return commands
}

private fun buildSuccessMessage(
    request: StorageConversionRequest,
    backupDirectory: Path,
    logFile: Path,
    verificationMessages: List<String>,
): String {
    val summary = verificationMessages.take(4).joinToString("\n")
    return buildString {
        append("Switched this project to ")
        append(request.target.displayName)
        append(" storage.\n\n")
        append("Backups: ")
        append(backupDirectory.invariantSeparatorsPathString)
        append("\nLog: ")
        append(logFile.invariantSeparatorsPathString)
        if (summary.isNotBlank()) {
            append("\n\nVerification:\n")
            append(summary)
        }
    }
}

private fun buildFailureMessage(
    failure: Throwable,
    backupDirectory: Path,
    logFile: Path,
    rollbackMessages: List<String>,
): String {
    return buildString {
        append(failure.message ?: "The storage conversion failed.")
        append("\n\n")
        if (rollbackMessages.isNotEmpty()) {
            append("Rollback:\n")
            append(rollbackMessages.joinToString("\n"))
            append("\n\n")
        }
        append("Backups: ")
        append(backupDirectory.invariantSeparatorsPathString)
        append("\nLog: ")
        append(logFile.invariantSeparatorsPathString)
    }
}

private fun buildRollbackFailureMessage(
    failure: Throwable,
    rollbackError: Throwable,
    backupDirectory: Path,
    logFile: Path,
): String {
    return buildString {
        append(failure.message ?: "The storage conversion failed.")
        append("\nRollback also failed: ")
        append(rollbackError.message ?: "Unknown rollback error.")
        append("\nBackups: ")
        append(backupDirectory.invariantSeparatorsPathString)
        append("\nLog: ")
        append(logFile.invariantSeparatorsPathString)
    }
}

private fun oneLine(script: String): String = script
    .lines()
    .joinToString(" ") { it.trim() }
    .replace(Regex("\\s+"), " ")
    .trim()

private fun php(script: String): String = script
    .trimIndent()
    .replace('§', '$')

private val YAML_EXTENSIONS = setOf("yaml", "yml")
private val CONTENT_FILE_EXTENSIONS = setOf("md", "markdown", "txt", "yaml", "yml")
private val IDENTIFIER_SEPARATOR = "\u001F"

private const val MINIMUM_CONVERSION_HEADROOM_BYTES = 50L * 1024L * 1024L
private const val MINIMUM_DATABASE_HEADROOM_BYTES = 25L * 1024L * 1024L

private val FILES_TO_RESTORE = listOf(
    ".env",
    "config/statamic/eloquent-driver.php",
)

private val FLAT_FILE_BACKUP_ROOTS = listOf(
    "content/addons",
    "content/assets",
    "content/collections",
    "content/globals",
    "content/navigation",
    "content/taxonomies",
    "content/trees",
    "resources/blueprints",
    "resources/fieldsets",
    "resources/forms",
    "resources/sites",
    "storage/forms",
    "storage/form-submissions",
    "storage/statamic/revisions",
)

private val REQUIRED_EXPORT_COMMANDS = listOf(
    "eloquent:export-collections",
    "eloquent:export-entries",
    "eloquent:export-blueprints",
    "eloquent:export-forms",
    "eloquent:export-globals",
    "eloquent:export-navs",
    "eloquent:export-taxonomies",
    "eloquent:export-assets",
    "eloquent:export-sites",
)

private val OPTIONAL_EXPORT_COMMANDS = listOf(
    "eloquent:export-addon-settings",
    "eloquent:export-revisions",
)

private val REQUIRED_IMPORT_COMMANDS = listOf(
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

private val OPTIONAL_IMPORT_COMMANDS = listOf(
    "eloquent:import-addon-settings",
    "eloquent:import-revisions",
)
