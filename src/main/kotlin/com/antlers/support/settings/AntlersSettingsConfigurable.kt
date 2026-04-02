package com.antlers.support.settings

import com.antlers.support.statamic.IndexingStatus
import com.antlers.support.statamic.StatamicDriver
import com.antlers.support.statamic.StatamicProjectCollections
import com.antlers.support.statamic.displayName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JButton
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
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
// Root: Languages & Frameworks > Statamic  (parent, no own UI — just children)
// ---------------------------------------------------------------------------

class AntlersSettingsConfigurable : SearchableConfigurable {
    override fun getId(): String = "com.statamic.toolkit.settings"
    override fun getDisplayName(): String = "Statamic"

    private val sections = listOf(
        SettingSection("Data Source", "Indexing status, resources, and collection blueprint fields."),
        SettingSection("Editor", "Typing, brace/quote auto-close, and semantic highlighting."),
        SettingSection("Completion", "Tags, parameters, variables, and suggestion behavior."),
        SettingSection("Navigation & Documentation", "Cmd+click navigation and Statamic hover docs."),
        SettingSection("Language Injection", "Alpine.js and PHP injection toggles.")
    )

    override fun createComponent(): JComponent {
        val baseFont = UIManager.getFont("Label.font")
        val bright = JBUI.CurrentTheme.Label.foreground()
        val dim = JBUI.CurrentTheme.Label.disabledForeground()
        val titleFont = baseFont.deriveFont(Font.BOLD, baseFont.size2D + 1f)
        val detailFont = baseFont.deriveFont(baseFont.size2D - 1f)

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4, 0, 12, 0)
        }

        content.add(JBLabel(
            "<html><body style='width: 520px'>Configure Statamic Toolkit settings for Antlers language support, indexing, navigation, completion, and editor behavior.</body></html>"
        ).apply {
            foreground = bright
            border = JBUI.Borders.emptyBottom(10)
        })

        content.add(JBLabel("Use the left sidebar to open a specific Statamic settings section.").apply {
            foreground = dim
            font = detailFont
            border = JBUI.Borders.emptyBottom(12)
        })
        content.add(TitledSeparator("Sections"))

        sections.forEachIndexed { index, section ->
            content.add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = JComponent.LEFT_ALIGNMENT
                border = JBUI.Borders.empty(4, 8, if (index == sections.lastIndex) 0 else 10, 8)
                add(JBLabel(section.title).apply {
                    foreground = bright
                    font = titleFont
                    alignmentX = JComponent.LEFT_ALIGNMENT
                })
                add(Box.createVerticalStrut(JBUI.scale(2)))
                add(JBLabel(section.description).apply {
                    foreground = dim
                    font = detailFont
                    alignmentX = JComponent.LEFT_ALIGNMENT
                })
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            })
        }

        return content
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
    private var resourcesPanel: JPanel? = null
    private var entryFieldsPanel: JPanel? = null
    private var refreshButton: JButton? = null
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
            alignmentX = JComponent.LEFT_ALIGNMENT
        }
        statusValue = JBLabel("—").apply {
            font = detailFont
            foreground = dim
            alignmentX = JComponent.LEFT_ALIGNMENT
        }
        resourcesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JComponent.LEFT_ALIGNMENT
        }
        entryFieldsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JComponent.LEFT_ALIGNMENT
        }
        refreshButton = JButton("Refresh").apply {
            addActionListener { refreshCollections() }
        }

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4, 0, 12, 0)
        }

        content.add(TitledSeparator("Connection"))
        content.add(driverValue)
        content.add(Box.createVerticalStrut(JBUI.scale(4)))
        content.add(statusValue)
        content.add(Box.createVerticalStrut(JBUI.scale(14)))

        content.add(TitledSeparator("Indexed Resources"))
        content.add(JBLabel("Discovered handles from content, forms, globals, navigation, and assets.").apply {
            foreground = dim
            font = detailFont
            border = JBUI.Borders.emptyBottom(8)
            alignmentX = JComponent.LEFT_ALIGNMENT
        })
        content.add(resourcesPanel)
        content.add(Box.createVerticalStrut(JBUI.scale(14)))

        content.add(TitledSeparator("Collection Entry Fields"))
        content.add(JBLabel("Blueprint fields available inside collection loops and entry contexts.").apply {
            foreground = dim
            font = detailFont
            border = JBUI.Borders.emptyBottom(8)
            alignmentX = JComponent.LEFT_ALIGNMENT
        })
        content.add(entryFieldsPanel)
        content.add(Box.createVerticalStrut(JBUI.scale(14)))

        val refreshRow = JPanel(BorderLayout()).apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            add(refreshButton)
        }
        content.add(refreshRow)
        content.add(Box.createVerticalGlue())

        panel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(content).apply {
                border = null
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }, BorderLayout.CENTER)
        }

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
        rebuildResourcesPanel(idx)
        rebuildEntryFieldsPanel(idx)

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

    private fun rebuildResourcesPanel(index: com.antlers.support.statamic.StatamicIndex) {
        val panel = resourcesPanel ?: return
        panel.removeAll()

        val resourceGroups = listOf(
            "Collections" to index.collections,
            "Navigations" to index.navigations,
            "Taxonomies" to index.taxonomies,
            "Global Sets" to index.globalSets,
            "Forms" to index.forms,
            "Asset Containers" to index.assetContainers,
        ).filter { it.second.isNotEmpty() }

        if (resourceGroups.isEmpty()) {
            panel.add(createEmptySectionLabel("No indexed resources yet."))
        } else {
            resourceGroups.forEachIndexed { index, (label, items) ->
                panel.add(createDataRow(label, items.size, wrapCommaSeparated(items)))
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
                panel.add(createDataRow(collection, fields.size, wrapCommaSeparated(fields)))
                if (idx != fieldGroups.lastIndex) {
                    panel.add(Box.createVerticalStrut(JBUI.scale(8)))
                }
            }
        }

        panel.revalidate()
        panel.repaint()
    }

    private fun createDataRow(label: String, count: Int, detail: String): JComponent {
        val bright = JBUI.CurrentTheme.Label.foreground()
        val dim = JBUI.CurrentTheme.Label.disabledForeground()
        val baseFont = UIManager.getFont("Label.font")
        val titleFont = baseFont.deriveFont(Font.BOLD)
        val detailFont = baseFont.deriveFont(baseFont.size2D - 1f)

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = JComponent.LEFT_ALIGNMENT

            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                alignmentX = JComponent.LEFT_ALIGNMENT
                add(JBLabel(label).apply {
                    foreground = bright
                    font = titleFont
                }, BorderLayout.WEST)
                add(JBLabel(count.toString()).apply {
                    foreground = dim
                    font = detailFont
                }, BorderLayout.EAST)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            })

            add(createDetailArea(detail).apply {
                border = JBUI.Borders.empty(2, 0, 0, 0)
            })

            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun createDetailArea(text: String): JTextArea {
        val dim = JBUI.CurrentTheme.Label.disabledForeground()
        val baseFont = UIManager.getFont("Label.font")
        return JTextArea(text).apply {
            isEditable = false
            isFocusable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = JBUI.Borders.empty()
            foreground = dim
            font = baseFont.deriveFont(baseFont.size2D - 1f)
            columns = 58
            alignmentX = JComponent.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun createEmptySectionLabel(text: String): JComponent {
        return JBLabel(text).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            font = UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").size2D - 1f)
            alignmentX = JComponent.LEFT_ALIGNMENT
        }
    }
}

private data class SettingSection(
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
