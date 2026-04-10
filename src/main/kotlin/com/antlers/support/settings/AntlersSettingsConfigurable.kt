package com.antlers.support.settings

import com.antlers.support.statamic.IndexingStatus
import com.antlers.support.statamic.StorageConversionRequest
import com.antlers.support.statamic.StorageConversionTarget
import com.antlers.support.statamic.StatamicDriver
import com.antlers.support.statamic.StatamicProjectCollections
import com.antlers.support.statamic.StatamicStorageConversionService
import com.antlers.support.statamic.displayName
import com.antlers.support.statamic.DatabaseConnectionConfig
import com.antlers.support.statamic.formatStorageSize
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogWrapper
import java.io.File
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JTextField
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.UIManager

// ---------------------------------------------------------------------------
// Shared binding helper
// ---------------------------------------------------------------------------

internal data class CheckboxField(
    val box: () -> JBCheckBox?,
    val read: (AntlersSettings.State) -> Boolean,
    val write: (AntlersSettings.State, Boolean) -> Unit
)

// ---------------------------------------------------------------------------
// Root: Languages & Frameworks > Statamic landing page
// ---------------------------------------------------------------------------

class AntlersSettingsConfigurable : SearchableConfigurable {
    override fun getId(): String = "com.statamic.toolkit.settings"
    override fun getDisplayName(): String = "Statamic"

    private val sections = listOf(
        "com.statamic.toolkit.settings.datasource" to "Data Source",
        "com.statamic.toolkit.settings.editor" to "Editor",
        "com.statamic.toolkit.settings.completion" to "Completion",
        "com.statamic.toolkit.settings.navigation" to "Navigation & Documentation",
        "com.statamic.toolkit.settings.injection" to "Language Injection",
    )

    override fun createComponent(): JComponent {
        val dim = JBUI.CurrentTheme.Label.foreground()

        val builder = FormBuilder.createFormBuilder()
            .addComponent(JBLabel(
                "Configure Antlers language support, indexing, navigation, completion, and editor behavior."
            ).apply {
                foreground = dim
                border = JBUI.Borders.emptyBottom(10)
            })

        for ((id, title) in sections) {
            builder.addComponent(ActionLink(title) {
                val source = it.source as? Component ?: return@ActionLink
                navigateToChild(source, id)
            }.apply {
                border = JBUI.Borders.empty(0, 24)
            })
        }

        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    /**
     * Walks up the Swing hierarchy to find the SettingsEditor and selects a child configurable by ID.
     */
    private fun navigateToChild(source: Component, childId: String) {
        var c: Component? = source
        while (c != null) {
            if (c is com.intellij.openapi.options.newEditor.SettingsEditor) {
                // Find the configurable by visiting all registered configurables
                val groups = com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil.getConfigurableGroup(
                    ProjectManager.getInstance().openProjects.firstOrNull(), true
                )
                val configurable = com.intellij.openapi.options.ex.ConfigurableVisitor.findById(
                    childId, listOf(groups)
                )
                if (configurable != null) c.select(configurable)
                return
            }
            c = c.parent
        }
    }

    override fun isModified(): Boolean = false
    override fun apply() {}
}

// ---------------------------------------------------------------------------
// Statamic > Data Source
// ---------------------------------------------------------------------------

class DataSourceConfigurable : Configurable {
    private var panel: JPanel? = null
    private var driverValue: JBLabel? = null
    private var statusValue: JBLabel? = null
    private var locationValue: JBLabel? = null
    private var sizeValue: JBLabel? = null
    private var recordsValue: JBLabel? = null
    private var resourcesPanel: JPanel? = null
    private var entryFieldsPanel: JPanel? = null
    private var refreshButton: JButton? = null
    private var convertToDbButton: JButton? = null
    private var convertToFileButton: JButton? = null
    private var statusTimer: Timer? = null

    override fun getDisplayName(): String = "Data Source"

    override fun createComponent(): JComponent {
        val baseFont = UIManager.getFont("Label.font")
        val bright = JBUI.CurrentTheme.Label.foreground()
        val dim = JBUI.CurrentTheme.Label.disabledForeground()
        val titleFont = baseFont.deriveFont(Font.BOLD, baseFont.size2D + 1f)
        val detailFont = baseFont.deriveFont(baseFont.size2D - 1f)

        driverValue = JBLabel("Detecting driver…").apply {
            font = titleFont
            foreground = bright
        }
        statusValue = JBLabel("—").apply {
            font = detailFont
            foreground = dim
        }
        locationValue = JBLabel("—").apply {
            font = detailFont
            foreground = dim
        }
        sizeValue = JBLabel("—").apply {
            font = detailFont
            foreground = dim
        }
        recordsValue = JBLabel("—").apply {
            font = detailFont
            foreground = dim
        }
        resourcesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        entryFieldsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        refreshButton = JButton("Refresh").apply {
            addActionListener { refreshCollections() }
        }

        val statusRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        statusRow.add(statusValue)
        statusRow.add(refreshButton)

        convertToDbButton = JButton("Convert to database").apply {
            font = detailFont
            addActionListener { showConvertToDatabaseDialog() }
        }
        convertToFileButton = JButton("Convert to flat file").apply {
            font = detailFont
            addActionListener { runExportToFlatFile() }
        }
        statusRow.add(convertToDbButton)
        statusRow.add(convertToFileButton)

        val indent = JBUI.Borders.emptyLeft(20)

        panel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Connection"))
            .addComponent(driverValue!!.apply { border = indent })
            .addComponent(statusRow.apply { border = indent })
            .addVerticalGap(8)
            .addComponent(TitledSeparator("Storage details"))
            .addComponent(createStorageDetailsPanel(indent))
            .addVerticalGap(8)
            .addComponent(TitledSeparator("Indexed resources"))
            .addComponent(resourcesPanel!!.apply { border = indent })
            .addVerticalGap(8)
            .addComponent(TitledSeparator("Collection entry fields"))
            .addComponent(entryFieldsPanel!!.apply { border = indent })
            .addComponentFillVertically(JPanel(), 0)
            .panel

        updateStatus()
        statusTimer = Timer(1000) { updateStatus() }.apply { start() }

        return panel!!.apply { border = JBUI.Borders.empty() }
    }

    private fun updateStatus() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val service = StatamicProjectCollections.getInstance(project)
        val conversionService = StatamicStorageConversionService.getInstance(project)

        driverValue?.text = if (service.driver == StatamicDriver.UNKNOWN) {
            "not detected"
        } else {
            service.driver.displayName()
        }

        statusValue?.text = when (service.status) {
            IndexingStatus.NOT_STARTED -> "not indexed yet"
            IndexingStatus.INDEXING -> service.currentStep.ifEmpty { "indexing..." }
            IndexingStatus.READY -> service.statusMessage
            IndexingStatus.ERROR -> service.statusMessage
        }
        statusValue?.foreground = when (service.status) {
            IndexingStatus.READY -> JBUI.CurrentTheme.Label.foreground()
            IndexingStatus.ERROR -> java.awt.Color(0xE05555)
            else -> JBUI.CurrentTheme.Label.disabledForeground()
        }

        val idx = service.index
        rebuildResourcesPanel(idx)
        rebuildEntryFieldsPanel(idx)
        updateStorageOverview(conversionService)

        if (service.status == IndexingStatus.READY || service.status == IndexingStatus.ERROR) {
            statusTimer?.stop()
        }
        refreshButton?.isEnabled = service.status != IndexingStatus.INDEXING

        // Show only the relevant convert button
        convertToDbButton?.isVisible = service.driver == StatamicDriver.FLAT_FILE || service.driver == StatamicDriver.UNKNOWN
        convertToFileButton?.isVisible = service.driver == StatamicDriver.ELOQUENT
    }

    private fun refreshCollections() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val basePath = project.basePath
        if (basePath != null) {
            val projectDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath)
            projectDir?.refresh(true, true)
        }
        StatamicProjectCollections.getInstance(project).refresh()
        statusTimer?.stop()
        statusTimer = Timer(500) { updateStatus() }.apply { start() }
    }

    private fun setConvertButtonsEnabled(enabled: Boolean) {
        convertToDbButton?.isEnabled = enabled
        convertToFileButton?.isEnabled = enabled
    }

    private fun updateStorageOverview(conversionService: StatamicStorageConversionService) {
        val snapshot = conversionService.currentStorageOverview()
        locationValue?.text = snapshot?.locationDescription ?: "—"
        sizeValue?.text = snapshot?.sizeBytes?.let(::formatStorageSize) ?: "—"
        recordsValue?.text = snapshot?.totalRecords?.toString() ?: "—"
    }

    private fun runExportToFlatFile() {
        startConversion(
            target = StorageConversionTarget.FLAT_FILE,
            databaseConfig = null,
        )
    }

    private fun showConvertToDatabaseDialog() {
        val project = currentProject() ?: return
        val basePath = project.basePath ?: return
        val envFile = File(basePath, ".env")
        val envValues = parseEnvFile(envFile)

        val dialog = DatabaseConfigDialog(
            dbConnection = envValues["DB_CONNECTION"] ?: "mysql",
            dbHost = envValues["DB_HOST"] ?: "127.0.0.1",
            dbPort = envValues["DB_PORT"] ?: "3306",
            dbDatabase = envValues["DB_DATABASE"] ?: "laravel",
            dbUsername = envValues["DB_USERNAME"] ?: "root",
            dbPassword = envValues["DB_PASSWORD"] ?: "",
        )

        if (!dialog.showAndGet()) return

        startConversion(
            target = StorageConversionTarget.DATABASE,
            databaseConfig = DatabaseConnectionConfig(
                connection = dialog.connectionField.text.trim(),
                host = dialog.hostField.text.trim(),
                port = dialog.portField.text.trim(),
                database = dialog.databaseField.text.trim(),
                username = dialog.usernameField.text.trim(),
                password = String(dialog.passwordField.password),
            ),
        )
    }

    private fun startConversion(
        target: StorageConversionTarget,
        databaseConfig: DatabaseConnectionConfig?,
    ) {
        val project = currentProject() ?: return
        val conversionService = StatamicStorageConversionService.getInstance(project)
        val request = StorageConversionRequest(
            target = target,
            databaseConfig = databaseConfig,
        )
        val analysis = try {
            conversionService.analyze(request)
        } catch (t: Throwable) {
            Messages.showErrorDialog(
                project,
                t.message ?: "Unable to analyze the requested storage conversion.",
                "Storage conversion failed"
            )
            return
        }
        if (analysis.errors.isNotEmpty()) {
            Messages.showErrorDialog(
                project,
                analysis.errors.joinToString("\n\n"),
                "Storage conversion blocked"
            )
            return
        }

        val confirmationDialog = StorageConversionConfirmationDialog(analysis)
        if (!confirmationDialog.showAndGet()) return
        val selectedResolution = confirmationDialog.selectedResolution
        if (selectedResolution == com.antlers.support.statamic.StorageConflictResolution.CANCEL && analysis.hasConflict) {
            return
        }

        setConvertButtonsEnabled(false)
        StorageConversionProgressDialog(
            project = project,
            request = request.copy(conflictResolution = selectedResolution),
            service = conversionService,
        ) { result ->
            setConvertButtonsEnabled(true)
            if (result.success) {
                val newDriver = target.driver
                driverValue?.text = newDriver.displayName()
                convertToDbButton?.isVisible = newDriver == StatamicDriver.FLAT_FILE
                convertToFileButton?.isVisible = newDriver == StatamicDriver.ELOQUENT
            }
            refreshCollections()
            updateStatus()
        }.show()
    }

    private fun currentProject(): Project? = ProjectManager.getInstance().openProjects.firstOrNull()

    private fun parseEnvFile(envFile: File): Map<String, String> {
        if (!envFile.exists()) return emptyMap()
        return envFile.readLines()
            .filter { it.contains('=') && !it.trimStart().startsWith('#') }
            .associate { line ->
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim().removeSurrounding("\"")
                key to value
            }
    }

    override fun disposeUIResources() { statusTimer?.stop(); statusTimer = null; panel = null }
    override fun isModified(): Boolean = false
    override fun apply() {}

    private fun rebuildResourcesPanel(index: com.antlers.support.statamic.StatamicIndex) {
        val panel = resourcesPanel ?: return
        panel.removeAll()

        val resourceGroups = listOf(
            "Collections" to index.collections,
            "Navigations" to index.navigations,
            "Taxonomies" to index.taxonomies,
            "Global sets" to index.globalSets,
            "Forms" to index.forms,
            "Asset containers" to index.assetContainers,
        ).filter { it.second.isNotEmpty() }

        if (resourceGroups.isEmpty()) {
            panel.add(createEmptySectionLabel("No indexed resources yet."))
        } else {
            resourceGroups.forEachIndexed { index, (label, items) ->
                panel.add(createDataRow(label, items.size, items))
                if (index != resourceGroups.lastIndex) {
                    panel.add(Box.createVerticalStrut(JBUI.scale(8)))
                }
            }
        }

        panel.revalidate()
        panel.repaint()
    }

    private fun rebuildEntryFieldsPanel(index: com.antlers.support.statamic.StatamicIndex) {
        val panel = entryFieldsPanel ?: return
        panel.removeAll()

        val fieldGroups = index.entryFieldsByCollection.entries
            .filter { it.value.isNotEmpty() }
            .sortedBy { it.key }

        if (fieldGroups.isEmpty()) {
            panel.add(createEmptySectionLabel("No indexed collection fields yet."))
        } else {
            fieldGroups.forEachIndexed { idx, (collection, fields) ->
                panel.add(createDataRow(collection, fields.size, fields))
                if (idx != fieldGroups.lastIndex) {
                    panel.add(Box.createVerticalStrut(JBUI.scale(8)))
                }
            }
        }

        panel.revalidate()
        panel.repaint()
    }

    private fun createDataRow(label: String, count: Int, items: List<String>): JComponent {
        val bright = JBUI.CurrentTheme.Label.foreground()
        val dim = JBUI.CurrentTheme.Label.disabledForeground()
        val baseFont = UIManager.getFont("Label.font")
        val titleFont = baseFont.deriveFont(Font.PLAIN)
        val detailFont = baseFont.deriveFont(baseFont.size2D - 1f)

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            // Header: "Collections (2)"
            add(JBLabel("$label ($count)").apply {
                foreground = bright
                font = titleFont
                alignmentX = Component.LEFT_ALIGNMENT
            })

            // Clickable inline items — click any name to copy to clipboard
            if (items.isNotEmpty()) {
                val itemsRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                items.forEachIndexed { i, item ->
                    itemsRow.add(JBLabel(item).apply {
                        foreground = dim
                        font = detailFont
                        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                        toolTipText = "Click to copy '$item'"
                        addMouseListener(object : java.awt.event.MouseAdapter() {
                            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(java.awt.datatransfer.StringSelection(item), null)
                                val original = foreground
                                foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                                javax.swing.Timer(600) { foreground = original }.apply { isRepeats = false; start() }
                            }
                            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                                foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                            }
                            override fun mouseExited(e: java.awt.event.MouseEvent) {
                                foreground = dim
                            }
                        })
                    })
                    if (i < items.lastIndex) {
                        itemsRow.add(JBLabel(", ").apply {
                            foreground = dim
                            font = detailFont
                        })
                    }
                }
                add(itemsRow)
            }
        }
    }

    private fun createEmptySectionLabel(text: String): JComponent {
        return JBLabel(text).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            font = UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").size2D - 1f)
            alignmentX = JComponent.LEFT_ALIGNMENT
        }
    }

    private fun createStorageDetailsPanel(indent: javax.swing.border.Border): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Location:"), locationValue!!.apply { border = indent })
            .addLabeledComponent(JBLabel("Size:"), sizeValue!!.apply { border = indent })
            .addLabeledComponent(JBLabel("Tracked records:"), recordsValue!!.apply { border = indent })
            .panel
    }
}

// ---------------------------------------------------------------------------
// Database configuration dialog for "Convert to Database"
// ---------------------------------------------------------------------------

private class DatabaseConfigDialog(
    dbConnection: String,
    dbHost: String,
    dbPort: String,
    dbDatabase: String,
    dbUsername: String,
    dbPassword: String,
) : DialogWrapper(true) {

    val connectionField = JTextField(dbConnection, 20)
    val hostField = JTextField(dbHost, 20)
    val portField = JTextField(dbPort, 20)
    val databaseField = JTextField(dbDatabase, 20)
    val usernameField = JTextField(dbUsername, 20)
    val passwordField = JPasswordField(dbPassword, 20)

    init {
        title = "Configure Database Connection"
        setOKButtonText("Convert")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("Connection:"), connectionField)
            .addLabeledComponent(JLabel("Host:"), hostField)
            .addLabeledComponent(JLabel("Port:"), portField)
            .addLabeledComponent(JLabel("Database:"), databaseField)
            .addLabeledComponent(JLabel("Username:"), usernameField)
            .addLabeledComponent(JLabel("Password:"), passwordField)
            .panel
    }

    override fun getPreferredFocusedComponent(): JComponent = databaseField
}

private data class SettingSection(
    val id: String,
    val title: String,
    val description: String
)

private fun wrapCommaSeparated(items: List<String>, maxLineLength: Int = 72): String {
    if (items.isEmpty()) return "—"

    val lines = mutableListOf<String>()
    val current = StringBuilder()

    for (item in items) {
        val piece = if (current.isEmpty()) item else ", $item"
        if (current.isNotEmpty() && current.length + piece.length > maxLineLength) {
            lines += current.toString()
            current.setLength(0)
            current.append(item)
        } else {
            current.append(piece)
        }
    }

    if (current.isNotEmpty()) {
        lines += current.toString()
    }

    return lines.joinToString("\n")
}

// ---------------------------------------------------------------------------
// Statamic > Editor
// ---------------------------------------------------------------------------

class EditorConfigurable : Configurable {
    private var panel: JPanel? = null
    private var autoCloseDelimiters: JBCheckBox? = null
    private var autoCloseQuotes: JBCheckBox? = null
    private var semanticHighlighting: JBCheckBox? = null

    private val fields: List<CheckboxField> by lazy {
        listOf(
            CheckboxField({ autoCloseDelimiters },  { it.enableAutoCloseDelimiters },  { s, v -> s.enableAutoCloseDelimiters  = v }),
            CheckboxField({ autoCloseQuotes },      { it.enableAutoCloseQuotes },      { s, v -> s.enableAutoCloseQuotes      = v }),
            CheckboxField({ semanticHighlighting }, { it.enableSemanticHighlighting }, { s, v -> s.enableSemanticHighlighting = v }),
        )
    }

    override fun getDisplayName(): String = "Editor"

    override fun createComponent(): JComponent {
        autoCloseDelimiters = JBCheckBox("Auto-close {{ }} delimiters")
        autoCloseQuotes = JBCheckBox("Auto-close quotes inside expressions")
        semanticHighlighting = JBCheckBox("Semantic highlighting for tag names and parameters")

        panel = FormBuilder.createFormBuilder()
            .addComponent(autoCloseDelimiters!!)
            .addComponent(autoCloseQuotes!!)
            .addComponent(semanticHighlighting!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = AntlersSettings.getInstance().state
        return fields.any { f -> f.box()?.isSelected != f.read(state) }
    }
    override fun apply() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.write(state, f.box()?.isSelected ?: true) }
    }
    override fun reset() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.box()?.isSelected = f.read(state) }
    }
}

// ---------------------------------------------------------------------------
// Statamic > Completion
// ---------------------------------------------------------------------------

class CompletionConfigurable : Configurable {
    private var panel: JPanel? = null
    private var tagCompletion: JBCheckBox? = null
    private var modifierCompletion: JBCheckBox? = null
    private var variableCompletion: JBCheckBox? = null
    private var parameterCompletion: JBCheckBox? = null

    private val fields: List<CheckboxField> by lazy {
        listOf(
            CheckboxField({ tagCompletion },       { it.enableTagCompletion },       { s, v -> s.enableTagCompletion       = v }),
            CheckboxField({ modifierCompletion },  { it.enableModifierCompletion },  { s, v -> s.enableModifierCompletion  = v }),
            CheckboxField({ variableCompletion },  { it.enableVariableCompletion },  { s, v -> s.enableVariableCompletion  = v }),
            CheckboxField({ parameterCompletion }, { it.enableParameterCompletion }, { s, v -> s.enableParameterCompletion = v }),
        )
    }

    override fun getDisplayName(): String = "Completion"

    override fun createComponent(): JComponent {
        tagCompletion = JBCheckBox("Tag name completion")
        modifierCompletion = JBCheckBox("Modifier completion (after |)")
        variableCompletion = JBCheckBox("Variable completion")
        parameterCompletion = JBCheckBox("Tag parameter completion (from=, limit=, etc.)")

        panel = FormBuilder.createFormBuilder()
            .addComponent(tagCompletion!!)
            .addComponent(modifierCompletion!!)
            .addComponent(variableCompletion!!)
            .addComponent(parameterCompletion!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = AntlersSettings.getInstance().state
        return fields.any { f -> f.box()?.isSelected != f.read(state) }
    }
    override fun apply() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.write(state, f.box()?.isSelected ?: true) }
    }
    override fun reset() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.box()?.isSelected = f.read(state) }
    }
}

// ---------------------------------------------------------------------------
// Statamic > Navigation & Documentation
// ---------------------------------------------------------------------------

class NavigationConfigurable : Configurable {
    private var panel: JPanel? = null
    private var partialNavigation: JBCheckBox? = null
    private var customTagNavigation: JBCheckBox? = null
    private var hoverDocumentation: JBCheckBox? = null

    private val fields: List<CheckboxField> by lazy {
        listOf(
            CheckboxField({ partialNavigation },   { it.enablePartialNavigation },   { s, v -> s.enablePartialNavigation   = v }),
            CheckboxField({ customTagNavigation }, { it.enableCustomTagNavigation }, { s, v -> s.enableCustomTagNavigation = v }),
            CheckboxField({ hoverDocumentation },  { it.enableHoverDocumentation },  { s, v -> s.enableHoverDocumentation  = v }),
        )
    }

    override fun getDisplayName(): String = "Navigation & Documentation"

    override fun createComponent(): JComponent {
        partialNavigation = JBCheckBox("Cmd+click navigation to partials")
        customTagNavigation = JBCheckBox("Cmd+click navigation to custom tag/modifier PHP classes")
        hoverDocumentation = JBCheckBox("Show Statamic documentation on hover")

        panel = FormBuilder.createFormBuilder()
            .addComponent(partialNavigation!!)
            .addComponent(customTagNavigation!!)
            .addComponent(hoverDocumentation!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = AntlersSettings.getInstance().state
        return fields.any { f -> f.box()?.isSelected != f.read(state) }
    }
    override fun apply() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.write(state, f.box()?.isSelected ?: true) }
    }
    override fun reset() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.box()?.isSelected = f.read(state) }
    }
}

// ---------------------------------------------------------------------------
// Statamic > Language Injection
// ---------------------------------------------------------------------------

class InjectionConfigurable : Configurable {
    private var panel: JPanel? = null
    private var phpInjection: JBCheckBox? = null
    private var alpineJsInjection: JBCheckBox? = null

    private val fields: List<CheckboxField> by lazy {
        listOf(
            CheckboxField({ phpInjection },      { it.enablePhpInjection },      { s, v -> s.enablePhpInjection      = v }),
            CheckboxField({ alpineJsInjection }, { it.enableAlpineJsInjection }, { s, v -> s.enableAlpineJsInjection = v }),
        )
    }

    override fun getDisplayName(): String = "Language Injection"

    override fun createComponent(): JComponent {
        phpInjection = JBCheckBox("PHP intelligence in {{? ?}} and {{$ $}} blocks")
        alpineJsInjection = JBCheckBox("Alpine.js intelligence in Antlers templates")

        panel = FormBuilder.createFormBuilder()
            .addComponent(phpInjection!!)
            .addComponent(alpineJsInjection!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = AntlersSettings.getInstance().state
        return fields.any { f -> f.box()?.isSelected != f.read(state) }
    }
    override fun apply() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.write(state, f.box()?.isSelected ?: true) }
    }
    override fun reset() {
        val state = AntlersSettings.getInstance().state
        fields.forEach { f -> f.box()?.isSelected = f.read(state) }
    }
}
