package com.antlers.support.statusbar

import com.antlers.support.AntlersIcons
import com.antlers.support.lsp.AntlersLspConnectionState
import com.antlers.support.lsp.AntlersLspStatusService
import com.antlers.support.lsp.AntlersLspStatusSnapshot
import com.antlers.support.settings.AntlersSettings
import com.antlers.support.settings.AntlersSettingsConfigurable
import com.antlers.support.statamic.IndexingStatus
import com.antlers.support.statamic.StatamicProjectCollections
import com.antlers.support.statamic.displayName
import com.antlers.support.statamic.entryFields
import com.antlers.support.statamic.totalResources
import com.antlers.support.ui.WrapLayout
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.icons.AllIcons
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.*

class StatamicStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "StatamicIndexingStatus"
    override fun getDisplayName(): String = "Statamic Indexing Status"
    override fun isAvailable(project: Project): Boolean = true
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = StatamicStatusBarWidget(project)
}

private class StatamicStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.IconPresentation {

    private var statusBar: StatusBar? = null
    private val statusChangeListener = Runnable { requestWidgetUpdate() }

    override fun ID(): String = "StatamicIndexingStatus"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        StatamicProjectCollections.getInstance(project).apply {
            addChangeListener(statusChangeListener)
            ensureLoaded()
        }
        project.getService(AntlersLspStatusService::class.java)?.addChangeListener(statusChangeListener)
        requestWidgetUpdate()
    }

    override fun dispose() {
        StatamicProjectCollections.getInstance(project).removeChangeListener(statusChangeListener)
        project.getService(AntlersLspStatusService::class.java)?.removeChangeListener(statusChangeListener)
        statusBar = null
    }
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getIcon(): Icon = AntlersIcons.FILE

    override fun getTooltipText(): String {
        val svc = StatamicProjectCollections.getInstance(project)
        return when (svc.status) {
            IndexingStatus.NOT_STARTED -> "Statamic: not indexed"
            IndexingStatus.INDEXING -> "Statamic: ${svc.currentStep}"
            IndexingStatus.READY -> "Statamic: ${svc.statusMessage}"
            IndexingStatus.ERROR -> "Statamic: ${svc.statusMessage}"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
        val panel = buildPopupPanel()
        val popup: JBPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(true)
            .createPopup()

        val component = event.component
        panel.doLayout()
        val h = panel.preferredSize.height + 8
        popup.show(RelativePoint(component, Point(0, -h)))
    }

    private fun buildPopupPanel(): JComponent {
        val svc = StatamicProjectCollections.getInstance(project)
        val idx = svc.index
        val dim = JBUI.CurrentTheme.Label.disabledForeground()
        val soft = Color(
            (dim.red + JBUI.CurrentTheme.Label.foreground().red) / 2,
            (dim.green + JBUI.CurrentTheme.Label.foreground().green) / 2,
            (dim.blue + JBUI.CurrentTheme.Label.foreground().blue) / 2
        )
        val bright = JBUI.CurrentTheme.Label.foreground()
        val success = Color(0x4CAF50)
        val warning = Color(0xE6A23C)
        val error = Color(0xE57373)
        val baseFont = UIManager.getFont("Label.font")
        val smallFont = baseFont.deriveFont(baseFont.size2D - 1.5f)
        val tinyFont = baseFont.deriveFont(baseFont.size2D - 2f)
        val boldFont = baseFont.deriveFont(Font.BOLD, baseFont.size2D + 1f)
        val lspStatus = project.getService(AntlersLspStatusService::class.java)?.snapshot()
        val settingsButtonRightNudge = JBUI.scale(2)

        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 12, 10, 12)
        }

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val settingsToolbar = createSettingsToolbar(root)

        fun sectionTitle(text: String) = JBLabel(text).apply {
            font = baseFont.deriveFont(Font.BOLD, baseFont.size2D - 0.5f)
            foreground = bright
            alignmentX = Component.LEFT_ALIGNMENT
        }

        fun sectionSpacer(height: Int = 10): JComponent =
            (Box.createVerticalStrut(JBUI.scale(height)) as JComponent).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }

        fun separator(): JSeparator =
            JSeparator().apply {
                alignmentX = Component.LEFT_ALIGNMENT
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            }

        fun statusLine(text: String, color: Color): JPanel {
            return JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                add(createStatusDot(color))
                add(Box.createHorizontalStrut(JBUI.scale(6)))
                add(JBLabel(text).apply {
                    font = smallFont
                    foreground = soft
                    alignmentY = Component.CENTER_ALIGNMENT
                })
            }
        }

        fun mutedInfoLine(text: String): JComponent = JBLabel(text).apply {
            font = tinyFont
            foreground = dim
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val driverName = svc.driver.displayName()

        val resourceGroups = listOf(
            StatusBarResourceGroup("Collections", "content/collections", idx.collections, "Open collection definitions"),
            StatusBarResourceGroup("Navigations", "content/navigation", idx.navigations, "Open navigation definitions"),
            StatusBarResourceGroup("Taxonomies", "content/taxonomies", idx.taxonomies, "Open taxonomy definitions"),
            StatusBarResourceGroup("Global Sets", "content/globals", idx.globalSets, "Open global set definitions"),
            StatusBarResourceGroup("Forms", "resources/forms", idx.forms, "Open form definitions"),
            StatusBarResourceGroup("Assets", "content/assets", idx.assetContainers, "Open asset container definitions")
        )
        val visibleResourceGroups = resourceGroups.filter { it.items.isNotEmpty() }
        val totalResources = idx.totalResources()
        val entryFieldCount = idx.entryFields.size
        val readySummary = buildString {
            append("Indexed • ")
            append(totalResources)
            append(" resources")
            if (entryFieldCount > 0) {
                append(" • ")
                append(entryFieldCount)
                append(" fields")
            }
        }

        val connectionPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JPanel(BorderLayout()).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                border = JBUI.Borders.empty(0, 0, 0, -settingsButtonRightNudge)
                maximumSize = Dimension(Int.MAX_VALUE, settingsToolbar.preferredSize.height)
                add(sectionTitle("Connection"), BorderLayout.WEST)
                add(settingsToolbar, BorderLayout.EAST)
            })
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(JBLabel(driverName).apply {
                font = boldFont
                foreground = bright
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(
                when (svc.status) {
                    IndexingStatus.READY -> statusLine(readySummary, success)
                    IndexingStatus.INDEXING -> statusLine(svc.currentStep.ifEmpty { "Indexing…" }, warning)
                    IndexingStatus.ERROR -> statusLine(svc.statusMessage.ifEmpty { "Indexing failed" }, error)
                    IndexingStatus.NOT_STARTED -> statusLine("Not indexed yet", dim)
                }
            )
            formatLastIndexedText(svc.lastIndexedAtMillis)?.let { lastIndexedText ->
                add(Box.createVerticalStrut(JBUI.scale(2)))
                add(mutedInfoLine(lastIndexedText).apply {
                    toolTipText = "Updated after the most recent successful Statamic index"
                })
            }
        }
        content.add(connectionPanel)
        content.add(sectionSpacer())
        content.add(separator())
        content.add(sectionSpacer(8))

        val resourcesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(sectionTitle("Resources"))
            add(Box.createVerticalStrut(JBUI.scale(6)))
        }

        fun addResourceRow(group: StatusBarResourceGroup) {
            val row = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = Component.LEFT_ALIGNMENT
            }

            val rowTooltip = group.toolTipText

            val disclosureLabel = JBLabel("+").apply {
                font = tinyFont
                foreground = soft
                border = JBUI.Borders.emptyRight(6)
            }

            val handlesRow = object : JPanel(WrapLayout()) {
                override fun getPreferredSize(): Dimension {
                    val maxWidth = parent?.width?.takeIf { it > 0 } ?: JBUI.scale(360)
                    val base = super.getPreferredSize()
                    if (base.width <= maxWidth) return base
                    // Force a layout at the capped width so WrapLayout reports the wrapped height.
                    val prev = size
                    size = Dimension(maxWidth, prev.height)
                    val wrapped = super.getPreferredSize()
                    size = prev
                    return Dimension(maxWidth, wrapped.height)
                }
            }.apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                isVisible = false
            }

            val headerPanel = JPanel(BorderLayout()).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(disclosureLabel)
                    add(createResourceLabel(group, bright, smallFont))
                }
                add(leftPanel, BorderLayout.WEST)
                add(JBLabel(group.items.size.toString()).apply {
                    font = tinyFont
                    foreground = soft
                    toolTipText = rowTooltip
                }, BorderLayout.EAST)
                toolTipText = rowTooltip
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        handlesRow.isVisible = !handlesRow.isVisible
                        disclosureLabel.text = if (handlesRow.isVisible) "\u2212" else "+"
                        row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
                        SwingUtilities.getWindowAncestor(row)?.pack()
                    }
                })
            }
            row.add(headerPanel)

            group.items.forEachIndexed { i, item ->
                handlesRow.add(JBLabel(item).apply {
                    font = tinyFont
                    foreground = dim
                    cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    toolTipText = "Click to copy '$item'"
                    addMouseListener(object : java.awt.event.MouseAdapter() {
                        override fun mouseClicked(e: java.awt.event.MouseEvent) {
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(java.awt.datatransfer.StringSelection(item), null)
                            val orig = foreground
                            foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                            javax.swing.Timer(600) { foreground = orig }.apply { isRepeats = false; start() }
                        }
                        override fun mouseEntered(e: java.awt.event.MouseEvent) {
                            foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                        }
                        override fun mouseExited(e: java.awt.event.MouseEvent) {
                            foreground = dim
                        }
                    })
                })
                if (i < group.items.lastIndex) {
                    handlesRow.add(JBLabel(", ").apply {
                        font = tinyFont
                        foreground = dim
                    })
                }
            }
            row.add(handlesRow)

            row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)

            resourcesPanel.add(row)
            resourcesPanel.add(Box.createVerticalStrut(JBUI.scale(10)))
        }

        if (visibleResourceGroups.isEmpty()) {
            resourcesPanel.add(mutedInfoLine(resourcesEmptyStateText(svc.status, totalResources)))
        } else {
            visibleResourceGroups.forEach(::addResourceRow)
        }

        content.add(resourcesPanel)
        content.add(sectionSpacer(6))
        content.add(separator())
        content.add(sectionSpacer(8))

        val quickLinksPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(sectionTitle("Quick Links"))
            add(Box.createVerticalStrut(JBUI.scale(6)))
        }

        quickLinksPanel.add(
            createQuickLinkRow(
                label = "Views",
                path = "resources/views",
                description = "Open template directory",
                toolTipText = "Open Statamic templates",
                bright = bright,
                dim = dim,
                smallFont = smallFont,
                tinyFont = tinyFont
            )
        )

        content.add(quickLinksPanel)
        content.add(sectionSpacer(6))
        content.add(separator())
        content.add(sectionSpacer(8))

        val checkbox = JBCheckBox("Auto-index").apply {
            font = smallFont
            isSelected = AntlersSettings.getInstance().state.enableAutoIndex
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener {
                AntlersSettings.getInstance().state.enableAutoIndex = isSelected
                val s = StatamicProjectCollections.getInstance(project)
                if (isSelected) s.startFileWatcher() else s.stopFileWatcher()
            }
        }

        val refreshLink = ActionLink("Refresh") {
            StatamicProjectCollections.getInstance(project).refresh()
            requestWidgetUpdate()
            SwingUtilities.getWindowAncestor(it.source as Component)?.dispose()
        }.apply {
            font = smallFont
            isEnabled = svc.status != IndexingStatus.INDEXING
        }

        val footer = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(checkbox, BorderLayout.WEST)
            add(refreshLink, BorderLayout.EAST)
        }
        content.add(footer)
        root.add(content)

        return root
    }

    private fun requestWidgetUpdate() {
        val application = ApplicationManager.getApplication()
        val update = {
            if (!project.isDisposed) {
                statusBar?.updateWidget(ID())
            }
        }

        if (application == null) {
            update()
        } else {
            application.invokeLater(update)
        }
    }

    private fun lspLine(
        snapshot: AntlersLspStatusSnapshot?,
        soft: Color,
        dim: Color,
        success: Color,
        warning: Color,
        error: Color,
        font: Font,
        labelFont: Font
    ): JPanel {
        val (label, color) = when (snapshot?.state) {
            AntlersLspConnectionState.CONNECTED -> snapshot.message to success
            AntlersLspConnectionState.STARTING -> snapshot.message to warning
            AntlersLspConnectionState.ERROR -> snapshot.message to error
            AntlersLspConnectionState.WAITING -> snapshot.message to dim
            null -> "Unavailable in this IDE" to dim
        }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(JBLabel("Antlers LSP").apply {
                foreground = soft
                this.font = labelFont
            })
            add(Box.createHorizontalStrut(JBUI.scale(6)))
            add(JBLabel(label).apply {
                foreground = color
                this.font = font
            })
        }
    }

    private fun createResourceLabel(group: StatusBarResourceGroup, foreground: Color, font: Font): JComponent {
        return createPathLink(
            label = group.label,
            path = group.path,
            foreground = foreground,
            font = font,
            toolTipText = group.toolTipText
        )
    }

    private fun createQuickLinkRow(
        label: String,
        path: String,
        description: String,
        toolTipText: String,
        bright: Color,
        dim: Color,
        smallFont: Font,
        tinyFont: Font
    ): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT

            add(createPathLink(label, path, bright, smallFont, toolTipText).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })

            add(JBLabel(description).apply {
                font = tinyFont
                foreground = dim
                border = JBUI.Borders.emptyTop(2)
                this.toolTipText = toolTipText
                alignmentX = Component.LEFT_ALIGNMENT
            })

            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun createPathLink(
        label: String,
        path: String,
        foreground: Color,
        font: Font,
        toolTipText: String
    ): JComponent {
        val target = findResourceDirectory(project, path)
        return if (target == null) {
            JBLabel(label).apply {
                this.font = font
                this.foreground = foreground
                this.toolTipText = toolTipText
            }
        } else {
            ActionLink(label) {
                openResourceDirectory(project, path)
                SwingUtilities.getWindowAncestor(it.source as Component)?.dispose()
            }.apply {
                this.font = font
                this.toolTipText = toolTipText
            }
        }
    }

    private fun createSettingsToolbar(targetComponent: JComponent): JComponent {
        val action = object : DumbAwareAction("Open Statamic settings", null, AllIcons.General.GearPlain) {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, AntlersSettingsConfigurable::class.java
                )
                SwingUtilities.getWindowAncestor(targetComponent)?.dispose()
            }
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "StatamicStatusWidget",
            DefaultActionGroup(action),
            true
        ).apply {
            setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY)
            setTargetComponent(targetComponent)
            isReservePlaceAutoPopupIcon = false
        }

        return toolbar.component.apply {
            isOpaque = false
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(24, 24)
            minimumSize = preferredSize
            maximumSize = preferredSize
            toolTipText = "Open Statamic settings"
        }
    }
}

private data class StatusBarResourceGroup(
    val label: String,
    val path: String,
    val items: List<String>,
    val toolTipText: String
)

private fun createStatusDot(color: Color): JComponent {
    val diameter = JBUI.scale(7)
    return object : JComponent() {
        init {
            preferredSize = Dimension(diameter, diameter)
            minimumSize = preferredSize
            maximumSize = preferredSize
            alignmentY = CENTER_ALIGNMENT
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.fillOval(0, 0, diameter, diameter)
            } finally {
                g2.dispose()
            }
        }
    }
}

private fun findResourceDirectory(project: Project, relativePath: String) =
    project.basePath
        ?.let { "$it/$relativePath" }
        ?.let(LocalFileSystem.getInstance()::findFileByPath)

private fun openResourceDirectory(project: Project, relativePath: String) {
    val target = project.basePath
        ?.let { "$it/$relativePath" }
        ?.let(LocalFileSystem.getInstance()::refreshAndFindFileByPath)
        ?: return
    val projectView = ProjectView.getInstance(project)
    projectView.changeView(ProjectViewPane.ID)
    projectView.select(null, target, false)
}

internal fun summarizeStatusBarHandles(items: List<String>, maxLength: Int = 42): String {
    if (items.isEmpty()) return ""

    val shown = mutableListOf<String>()
    for ((index, item) in items.withIndex()) {
        val hiddenAfterThis = items.size - index - 1
        val candidateItems = shown + item
        val candidateText = candidateItems.joinToString(", ")
        val candidateWithSuffix = if (hiddenAfterThis > 0) "$candidateText +$hiddenAfterThis" else candidateText

        if (candidateWithSuffix.length <= maxLength || shown.isEmpty()) {
            shown += item
            continue
        }
        break
    }

    val hiddenCount = items.size - shown.size
    if (hiddenCount <= 0) {
        return truncateStatusBarHandleText(shown.joinToString(", "), maxLength)
    }

    val suffix = " +$hiddenCount"
    var visibleText = shown.joinToString(", ")
    while (shown.size > 1 && visibleText.length + suffix.length > maxLength) {
        shown.removeAt(shown.lastIndex)
        visibleText = shown.joinToString(", ")
    }

    if (visibleText.length + suffix.length <= maxLength) {
        return visibleText + suffix
    }

    return truncateStatusBarHandleText(visibleText, maxLength - suffix.length) + suffix
}

private fun truncateStatusBarHandleText(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    if (maxLength <= 1) return "…"
    return text.take(maxLength - 1).trimEnd() + "…"
}

internal fun formatLastIndexedText(lastIndexedAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
    if (lastIndexedAtMillis == null) return null

    val elapsed = (nowMillis - lastIndexedAtMillis).coerceAtLeast(0L)
    val relativeText = when {
        elapsed < 60_000L -> "just now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L}m ago"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L}h ago"
        else -> "${elapsed / 86_400_000L}d ago"
    }

    return "Last indexed $relativeText"
}

internal fun resourcesEmptyStateText(status: IndexingStatus, totalResources: Int): String {
    if (totalResources > 0) return ""

    return when (status) {
        IndexingStatus.INDEXING -> "Resources will appear when indexing finishes."
        IndexingStatus.ERROR -> "Run Refresh to try indexing again."
        IndexingStatus.READY -> "No Statamic resources found in this project."
        IndexingStatus.NOT_STARTED -> "Run Refresh to index Statamic resources."
    }
}
