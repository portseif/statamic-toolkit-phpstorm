package com.antlers.support.statusbar

import com.antlers.support.AntlersIcons
import com.antlers.support.lsp.AntlersLspConnectionState
import com.antlers.support.lsp.AntlersLspStatusService
import com.antlers.support.lsp.AntlersLspStatusSnapshot
import com.antlers.support.settings.AntlersSettings
import com.antlers.support.statamic.IndexingStatus
import com.antlers.support.statamic.StatamicProjectCollections
import com.antlers.support.statamic.displayName
import com.antlers.support.statamic.entryFields
import com.antlers.support.statamic.totalResources
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
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

    private fun buildPopupPanel(): JPanel {
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

        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 12)
        }

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

        val driverName = svc.driver.displayName()

        val resourceGroups = listOf(
            StatusBarResourceGroup("Collections", "content/collections", idx.collections),
            StatusBarResourceGroup("Navigations", "content/navigation", idx.navigations),
            StatusBarResourceGroup("Taxonomies", "content/taxonomies", idx.taxonomies),
            StatusBarResourceGroup("Global Sets", "content/globals", idx.globalSets),
            StatusBarResourceGroup("Forms", "resources/forms", idx.forms),
            StatusBarResourceGroup("Assets", "content/assets", idx.assetContainers)
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
            add(sectionTitle("Connection"))
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
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(lspLine(lspStatus, soft, dim, success, warning, error, smallFont))
        }
        root.add(connectionPanel)
        root.add(sectionSpacer())
        root.add(separator())
        root.add(sectionSpacer(8))

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

            val topLine = JPanel(BorderLayout()).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                isOpaque = false
                add(createResourceLabel(group, bright, smallFont), BorderLayout.WEST)
                add(JBLabel(group.items.size.toString()).apply {
                    font = tinyFont
                    foreground = soft
                }, BorderLayout.EAST)
            }
            row.add(topLine)

            row.add(JBLabel(summarizeStatusBarHandles(group.items)).apply {
                font = tinyFont
                foreground = dim
                border = JBUI.Borders.empty(2, 0, 0, 0)
                toolTipText = group.items.joinToString(", ")
                alignmentX = Component.LEFT_ALIGNMENT
            })

            row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)

            resourcesPanel.add(row)
            resourcesPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        }

        if (visibleResourceGroups.isEmpty()) {
            resourcesPanel.add(JBLabel("No indexed resources yet").apply {
                font = tinyFont
                foreground = dim
                alignmentX = Component.LEFT_ALIGNMENT
            })
        } else {
            visibleResourceGroups.forEach(::addResourceRow)
        }

        root.add(resourcesPanel)
        root.add(sectionSpacer(6))
        root.add(separator())
        root.add(sectionSpacer(8))

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
                bright = bright,
                dim = dim,
                smallFont = smallFont,
                tinyFont = tinyFont
            )
        )

        root.add(quickLinksPanel)
        root.add(sectionSpacer(6))
        root.add(separator())
        root.add(sectionSpacer(8))

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
            alignmentX = Component.LEFT_ALIGNMENT
            add(checkbox, BorderLayout.WEST)
            add(refreshLink, BorderLayout.EAST)
        }
        root.add(footer)

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
        font: Font
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
                this.font = font
            })
            add(Box.createHorizontalStrut(JBUI.scale(8)))
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
            font = font
        )
    }

    private fun createQuickLinkRow(
        label: String,
        path: String,
        description: String,
        bright: Color,
        dim: Color,
        smallFont: Font,
        tinyFont: Font
    ): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT

            add(createPathLink(label, path, bright, smallFont).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })

            add(JBLabel(description).apply {
                font = tinyFont
                foreground = dim
                border = JBUI.Borders.empty(2, 0, 0, 0)
                toolTipText = path
                alignmentX = Component.LEFT_ALIGNMENT
            })

            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun createPathLink(label: String, path: String, foreground: Color, font: Font): JComponent {
        val target = findResourceDirectory(project, path)
        return if (target == null) {
            JBLabel(label).apply {
                this.font = font
                this.foreground = foreground
            }
        } else {
            ActionLink(label) {
                openResourceDirectory(project, path)
                SwingUtilities.getWindowAncestor(it.source as Component)?.dispose()
            }.apply {
                this.font = font
                toolTipText = "Open $path"
            }
        }
    }
}

private data class StatusBarResourceGroup(
    val label: String,
    val path: String,
    val items: List<String>
)

private fun createStatusDot(color: Color): JComponent {
    val diameter = JBUI.scale(7)
    return object : JComponent() {
        init {
            preferredSize = Dimension(diameter, diameter)
            minimumSize = preferredSize
            maximumSize = preferredSize
            alignmentY = Component.CENTER_ALIGNMENT
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
