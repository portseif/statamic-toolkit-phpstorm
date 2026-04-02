package com.antlers.support.settings

import com.antlers.support.statamic.IndexingStatus
import com.antlers.support.statamic.StatamicDriver
import com.antlers.support.statamic.StatamicProjectCollections
import com.antlers.support.statamic.displayName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

// ---------------------------------------------------------------------------
// Shared binding helper
// ---------------------------------------------------------------------------

internal data class CheckboxField(
    val box: () -> JBCheckBox?,
    val read: (AntlersSettings.State) -> Boolean,
    val write: (AntlersSettings.State, Boolean) -> Unit
)

// ---------------------------------------------------------------------------
// Root: Languages & Frameworks > Statamic  (parent, no own UI — just children)
// ---------------------------------------------------------------------------

class AntlersSettingsConfigurable : SearchableConfigurable {
    override fun getId(): String = "com.statamic.toolkit.settings"
    override fun getDisplayName(): String = "Statamic"

    private val childIds = listOf(
        "com.statamic.toolkit.settings.datasource" to "Data Source",
        "com.statamic.toolkit.settings.editor" to "Editor",
        "com.statamic.toolkit.settings.completion" to "Completion",
        "com.statamic.toolkit.settings.navigation" to "Navigation & Documentation",
        "com.statamic.toolkit.settings.injection" to "Language Injection",
    )

    override fun createComponent(): JComponent {
        val description = JBLabel(
            "Configure Statamic Toolkit plugin settings for Antlers template language support."
        ).apply {
            border = JBUI.Borders.emptyBottom(12)
        }

        val builder = FormBuilder.createFormBuilder()
            .addComponent(description)

        for ((id, name) in childIds) {
            val link = JBLabel(name).apply {
                foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                font = font.deriveFont(font.size.toFloat() + 1f)
                border = JBUI.Borders.empty(2, 8)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(
                            ProjectManager.getInstance().openProjects.firstOrNull(),
                            id
                        )
                    }
                })
            }
            builder.addComponent(link)
        }

        return builder.addComponentFillVertically(JPanel(), 0).panel
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
    private var resourceLabels: MutableMap<String, Pair<JBLabel, JBLabel>> = mutableMapOf()
    private var refreshButton: JButton? = null
    private var statusTimer: Timer? = null

    override fun getDisplayName(): String = "Data Source"

    override fun createComponent(): JComponent {
        driverValue = JBLabel("detecting...")
        statusValue = JBLabel("—")
        refreshButton = JButton("Refresh").apply {
            addActionListener { refreshCollections() }
        }

        val resources = listOf("Collections", "Navigations", "Taxonomies", "Global Sets", "Forms", "Asset Containers")
        for (name in resources) {
            resourceLabels[name] = Pair(
                JBLabel("0").apply { foreground = JBUI.CurrentTheme.Label.disabledForeground() },
                JBLabel("—").apply { foreground = JBUI.CurrentTheme.Label.disabledForeground() }
            )
        }

        val builder = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Connection"))
            .addLabeledComponent("Driver:", driverValue!!)
            .addLabeledComponent("Status:", statusValue!!)
            .addComponent(TitledSeparator("Indexed Resources"))

        for ((name, labels) in resourceLabels) {
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(labels.first)  // count
                add(JBLabel("—").apply { foreground = JBUI.CurrentTheme.Label.disabledForeground() })
                add(labels.second) // handles
            }
            builder.addLabeledComponent("$name:", row)
        }

        builder.addComponent(TitledSeparator(""))
        val refreshRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 4)).apply {
            add(refreshButton)
        }
        builder.addComponent(refreshRow)
        builder.addComponentFillVertically(JPanel(), 0)

        panel = builder.panel

        updateStatus()
        statusTimer = Timer(1000) { updateStatus() }.apply { start() }

        return panel!!
    }

    private fun updateStatus() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val service = StatamicProjectCollections.getInstance(project)

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
        fun updateRow(name: String, items: List<String>) {
            val (countLabel, handlesLabel) = resourceLabels[name] ?: return
            countLabel.text = "${items.size}"
            handlesLabel.text = if (items.isNotEmpty()) items.joinToString(", ") else "—"
            val active = items.isNotEmpty()
            countLabel.foreground = if (active) JBUI.CurrentTheme.Label.foreground() else JBUI.CurrentTheme.Label.disabledForeground()
            handlesLabel.foreground = if (active) JBUI.CurrentTheme.Label.foreground() else JBUI.CurrentTheme.Label.disabledForeground()
        }
        updateRow("Collections", idx.collections)
        updateRow("Navigations", idx.navigations)
        updateRow("Taxonomies", idx.taxonomies)
        updateRow("Global Sets", idx.globalSets)
        updateRow("Forms", idx.forms)
        updateRow("Asset Containers", idx.assetContainers)

        if (service.status == IndexingStatus.READY || service.status == IndexingStatus.ERROR) {
            statusTimer?.stop()
        }
        refreshButton?.isEnabled = service.status != IndexingStatus.INDEXING
    }

    private fun refreshCollections() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        StatamicProjectCollections.getInstance(project).refresh()
        statusTimer?.stop()
        statusTimer = Timer(500) { updateStatus() }.apply { start() }
    }

    override fun disposeUIResources() { statusTimer?.stop(); statusTimer = null; panel = null }
    override fun isModified(): Boolean = false
    override fun apply() {}
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
