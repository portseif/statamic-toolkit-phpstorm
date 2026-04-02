package com.antlers.support.statamic

import com.antlers.support.settings.AntlersSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

enum class StatamicDriver { FLAT_FILE, ELOQUENT, UNKNOWN }
enum class IndexingStatus { NOT_STARTED, INDEXING, READY, ERROR }

data class StatamicIndex(
    val collections: List<String> = emptyList(),
    val navigations: List<String> = emptyList(),
    val taxonomies: List<String> = emptyList(),
    val globalSets: List<String> = emptyList(),
    val forms: List<String> = emptyList(),
    val assetContainers: List<String> = emptyList(),
    val entryFieldsByCollection: Map<String, List<String>> = emptyMap(),
)

/**
 * Discovers and caches Statamic resource handles for a project.
 * Auto-detects whether the project uses flat-file or Eloquent driver.
 * Queries via artisan tinker for Eloquent driver projects.
 */
@Service(Service.Level.PROJECT)
class StatamicProjectCollections(private val project: Project) {

    /** Convenience accessor for collection handles (used by completion). */
    val handles: List<String> get() = index.collections

    @Volatile var index: StatamicIndex = StatamicIndex()
        private set

    @Volatile var driver: StatamicDriver = StatamicDriver.UNKNOWN
        private set

    @Volatile var status: IndexingStatus = IndexingStatus.NOT_STARTED
        private set

    @Volatile var statusMessage: String = ""
        private set

    @Volatile var currentStep: String = ""
        private set

    @Volatile private var loaded = false
    @Volatile private var refreshInProgress = false
    @Volatile private var pendingRefresh = false

    private val refreshLock = Any()
    private val listeners = CopyOnWriteArrayList<Runnable>()

    fun ensureLoaded() {
        if (loaded) return
        refresh()
        if (AntlersSettings.getInstance().state.enableAutoIndex) {
            startFileWatcher()
        }
    }

    fun refresh() {
        loaded = true
        val previousState = snapshotState()

        val shouldStart = synchronized(refreshLock) {
            if (refreshInProgress) {
                pendingRefresh = true
                false
            } else {
                refreshInProgress = true
                true
            }
        }

        if (!shouldStart) return

        setState(
            status = IndexingStatus.INDEXING,
            currentStep = "Detecting driver...",
            statusMessage = "Indexing Statamic project..."
        )

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Indexing Statamic resources", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false

                    val basePath = project.basePath ?: run {
                        setState(
                            status = IndexingStatus.ERROR,
                            statusMessage = "No project base path",
                            currentStep = ""
                        )
                        return
                    }

                    indicator.text = "Detecting Statamic driver..."
                    indicator.fraction = 0.1
                    val detectedDriver = detectDriver(basePath)
                    setState(driver = detectedDriver)

                    if (indicator.isCanceled) {
                        restoreState(previousState)
                        return
                    }

                    if (detectedDriver == StatamicDriver.ELOQUENT) {
                        indexViaArtisan(basePath, indicator)
                    } else {
                        indexViaFlatFile(basePath, indicator)
                    }
                } finally {
                    finishRefresh()
                }
            }
        })
    }

    private fun indexViaArtisan(basePath: String, indicator: ProgressIndicator? = null) {
        val phpPath = findPhp()
        if (phpPath == null) {
            setState(
                status = IndexingStatus.ERROR,
                statusMessage = "PHP not found — cannot index Eloquent data",
                currentStep = ""
            )
            return
        }

        // Query all resources in a single artisan call for efficiency
        setState(currentStep = "Querying database...")
        indicator?.text = "Querying Statamic database..."
        indicator?.fraction = 0.3
        val script = """
            echo json_encode([
                'collections' => \Statamic\Facades\Collection::all()->map->handle()->values(),
                'navigations' => \Statamic\Facades\Nav::all()->map->handle()->values(),
                'taxonomies' => \Statamic\Facades\Taxonomy::all()->map->handle()->values(),
                'global_sets' => \Statamic\Facades\GlobalSet::all()->map->handle()->values(),
                'forms' => \Statamic\Facades\Form::all()->map->handle()->values(),
                'asset_containers' => \Statamic\Facades\AssetContainer::all()->map->handle()->values(),
            ]);
        """.trimIndent().replace("\n", " ")

        try {
            val cmd = GeneralCommandLine(phpPath, "artisan", "tinker", "--execute", script)
                .withWorkDirectory(basePath)

            val output = ScriptRunnerUtil.getProcessOutput(cmd).trim()

            if (output.startsWith("{")) {
                val parsed = parseJsonObject(output)
                indicator?.text = "Scanning entry blueprints..."
                indicator?.fraction = 0.7
                val entryFieldsByCollection = StatamicBlueprintFieldScanner.scanCollectionEntryFields(basePath)

                val nextIndex = StatamicIndex(
                    collections = parsed["collections"].orEmpty(),
                    navigations = parsed["navigations"].orEmpty(),
                    taxonomies = parsed["taxonomies"].orEmpty(),
                    globalSets = parsed["global_sets"].orEmpty(),
                    forms = parsed["forms"].orEmpty(),
                    assetContainers = parsed["asset_containers"].orEmpty(),
                    entryFieldsByCollection = entryFieldsByCollection,
                )

                indicator?.text = "Processing results..."
                indicator?.fraction = 0.9

                indicator?.fraction = 1.0
                setState(
                    index = nextIndex,
                    status = IndexingStatus.READY,
                    currentStep = "",
                    statusMessage = nextIndex.readyStatusMessage()
                )
            } else {
                setState(
                    status = IndexingStatus.ERROR,
                    statusMessage = "Unexpected artisan output",
                    currentStep = ""
                )
            }
        } catch (e: Exception) {
            setState(
                status = IndexingStatus.ERROR,
                statusMessage = "Artisan failed: ${e.message?.take(80)}",
                currentStep = ""
            )
        }
    }

    private fun indexViaFlatFile(basePath: String, indicator: ProgressIndicator? = null) {
        setState(currentStep = "Scanning content directory...")

        indicator?.text = "Scanning collections..."
        indicator?.fraction = 0.2
        val collections = scanDirectory("$basePath/content/collections")

        indicator?.text = "Scanning navigations..."
        indicator?.fraction = 0.35
        val navigations = scanDirectory("$basePath/content/navigation")

        indicator?.text = "Scanning taxonomies..."
        indicator?.fraction = 0.5
        val taxonomies = scanDirectory("$basePath/content/taxonomies")

        indicator?.text = "Scanning globals..."
        indicator?.fraction = 0.65
        val globalSets = scanYamlFiles("$basePath/content/globals")

        indicator?.text = "Scanning forms..."
        indicator?.fraction = 0.75
        val forms = scanYamlFiles("$basePath/resources/forms")

        indicator?.text = "Scanning entry blueprints..."
        indicator?.fraction = 0.85
        val entryFieldsByCollection = StatamicBlueprintFieldScanner.scanCollectionEntryFields(basePath)

        indicator?.text = "Scanning asset containers..."
        indicator?.fraction = 0.95
        val assetContainers = scanYamlFiles("$basePath/content/assets")

        val nextIndex = StatamicIndex(
            collections = collections,
            navigations = navigations,
            taxonomies = taxonomies,
            globalSets = globalSets,
            forms = forms,
            assetContainers = assetContainers,
            entryFieldsByCollection = entryFieldsByCollection,
        )

        indicator?.fraction = 1.0
        setState(
            index = nextIndex,
            status = IndexingStatus.READY,
            currentStep = "",
            statusMessage = nextIndex.readyStatusMessage()
        )
    }

    private fun scanDirectory(path: String): List<String> {
        val dir = LocalFileSystem.getInstance().findFileByPath(path) ?: return emptyList()
        val result = mutableSetOf<String>()
        for (child in dir.children) {
            when {
                child.isDirectory -> result.add(child.name)
                child.name.endsWith(".yaml") || child.name.endsWith(".yml") ->
                    result.add(child.nameWithoutExtension)
            }
        }
        return result.sorted()
    }

    private fun scanYamlFiles(path: String): List<String> {
        val dir = LocalFileSystem.getInstance().findFileByPath(path) ?: return emptyList()
        return dir.children
            .filter { it.name.endsWith(".yaml") || it.name.endsWith(".yml") }
            .map { it.nameWithoutExtension }
            .sorted()
    }

    private fun detectDriver(basePath: String): StatamicDriver {
        val configFile = File(basePath, "config/statamic/eloquent-driver.php")
        if (!configFile.exists()) return StatamicDriver.FLAT_FILE

        try {
            val content = configFile.readText()
            val collectionsSection = content.substringAfter("'collections'", "")
            if (collectionsSection.isNotEmpty()) {
                val driverLine = collectionsSection.substringBefore("]")
                if (driverLine.contains("'eloquent'")) {
                    return StatamicDriver.ELOQUENT
                }
            }
        } catch (_: Exception) {}

        return StatamicDriver.FLAT_FILE
    }

    /**
     * Parses a simple JSON object of string arrays: {"key": ["a","b"], ...}
     */
    private fun parseJsonObject(json: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        // Match "key":["val1","val2",...] patterns
        val entryPattern = Regex(""""(\w+)":\s*\[([^\]]*)]""")
        for (match in entryPattern.findAll(json)) {
            val key = match.groupValues[1]
            val valuesStr = match.groupValues[2]
            val values = if (valuesStr.isBlank()) emptyList()
            else valuesStr.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
            result[key] = values
        }
        return result
    }

    private fun findPhp(): String? {
        val user = System.getProperty("user.name")
        val candidates = listOf(
            "/Users/$user/Library/Application Support/Herd/bin/php",
            "/opt/homebrew/bin/php",
            "/usr/local/bin/php",
            "/usr/bin/php"
        )
        for (path in candidates) {
            if (File(path).exists()) return path
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Auto-index file watcher
    // -------------------------------------------------------------------------

    private var watcherDisposable: Disposable? = null
    private var debounceTimer: javax.swing.Timer? = null

    fun startFileWatcher() {
        if (watcherDisposable != null) return
        val basePath = project.basePath ?: return

        val disposable = Disposer.newDisposable("StatamicFileWatcher")
        watcherDisposable = disposable

        project.messageBus.connect(disposable).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val relevant = events.any { event ->
                        val path = event.path
                        path.startsWith("$basePath/content/") ||
                            path.startsWith("$basePath/resources/blueprints/") ||
                            path.startsWith("$basePath/resources/forms/") ||
                            path.endsWith(".yaml") || path.endsWith(".yml")
                    }
                    if (relevant) {
                        // Debounce: wait 2 seconds after last change before re-indexing
                        debounceTimer?.stop()
                        debounceTimer = javax.swing.Timer(2000) { refresh() }.apply {
                            isRepeats = false
                            start()
                        }
                    }
                }
            }
        )
    }

    fun stopFileWatcher() {
        debounceTimer?.stop()
        debounceTimer = null
        watcherDisposable?.let { Disposer.dispose(it) }
        watcherDisposable = null
    }

    fun addChangeListener(listener: Runnable) {
        listeners += listener
    }

    fun removeChangeListener(listener: Runnable) {
        listeners -= listener
    }

    private fun snapshotState() = InternalState(index, driver, status, statusMessage, currentStep)

    private fun restoreState(state: InternalState) {
        setState(
            index = state.index,
            driver = state.driver,
            status = state.status,
            statusMessage = state.statusMessage,
            currentStep = state.currentStep
        )
    }

    private fun setState(
        index: StatamicIndex = this.index,
        driver: StatamicDriver = this.driver,
        status: IndexingStatus = this.status,
        statusMessage: String = this.statusMessage,
        currentStep: String = this.currentStep
    ) {
        val changed = this.index != index ||
            this.driver != driver ||
            this.status != status ||
            this.statusMessage != statusMessage ||
            this.currentStep != currentStep

        this.index = index
        this.driver = driver
        this.status = status
        this.statusMessage = statusMessage
        this.currentStep = currentStep

        if (changed) {
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        if (listeners.isEmpty()) return

        val application = ApplicationManager.getApplication()
        if (application == null || application.isUnitTestMode) {
            listeners.forEach(Runnable::run)
            return
        }

        application.invokeLater {
            if (project.isDisposed) return@invokeLater
            listeners.forEach(Runnable::run)
        }
    }

    private fun finishRefresh() {
        val rerun = synchronized(refreshLock) {
            refreshInProgress = false
            val shouldRerun = pendingRefresh
            pendingRefresh = false
            shouldRerun
        }

        if (rerun && !project.isDisposed) {
            refresh()
        }
    }

    companion object {
        fun getInstance(project: Project): StatamicProjectCollections =
            project.getService(StatamicProjectCollections::class.java)
    }
}

private data class InternalState(
    val index: StatamicIndex,
    val driver: StatamicDriver,
    val status: IndexingStatus,
    val statusMessage: String,
    val currentStep: String
)
