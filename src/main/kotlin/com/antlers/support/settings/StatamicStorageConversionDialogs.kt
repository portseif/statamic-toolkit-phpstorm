package com.antlers.support.settings

import com.antlers.support.statamic.StorageConflictResolution
import com.antlers.support.statamic.StorageConversionAnalysis
import com.antlers.support.statamic.StorageConversionPhase
import com.antlers.support.statamic.StorageConversionRequest
import com.antlers.support.statamic.StorageConversionResult
import com.antlers.support.statamic.StorageSnapshot
import com.antlers.support.statamic.StatamicDriver
import com.antlers.support.statamic.StatamicStorageConversionService
import com.antlers.support.statamic.displayName
import com.antlers.support.statamic.formatStorageSize
import com.intellij.ui.JBColor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JComponent
import java.awt.RenderingHints
import kotlin.io.path.invariantSeparatorsPathString
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager

internal class StorageConversionConfirmationDialog(
    private val analysis: StorageConversionAnalysis,
) : DialogWrapper(true) {
    private val resolutionBox = JComboBox(
        analysis.supportedResolutions.toTypedArray()
    ).apply {
        selectedItem = if (analysis.hasConflict) {
            StorageConflictResolution.MERGE
        } else {
            analysis.request.conflictResolution
        }
        prototypeDisplayValue = StorageConflictResolution.OVERWRITE
        isEnabled = analysis.hasConflict
        renderer = ResolutionListCellRenderer()
    }

    val selectedResolution: StorageConflictResolution
        get() = resolutionBox.selectedItem as? StorageConflictResolution
            ?: StorageConflictResolution.MERGE

    init {
        title = "Confirm Storage Conversion"
        setOKButtonText("Convert to ${analysis.request.target.displayName}")
        isResizable = false
        resolutionBox.preferredSize = Dimension(
            maxOf(resolutionBox.preferredSize.width, JBUI.scale(240)),
            resolutionBox.preferredSize.height,
        )
        resolutionBox.maximumSize = Dimension(Int.MAX_VALUE, resolutionBox.preferredSize.height)
        init()
    }

    override fun createCenterPanel(): JComponent {
        resolutionBox.apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            putClientProperty("JComponent.roundRect", true)
        }

        val content = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(18, 14, 4, 14)
        }

        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = JBUI.insetsBottom(10)
        }

        content.add(createSectionLabel("Storage configuration comparison"), constraints)

        constraints.gridy++
        constraints.insets = JBUI.insetsBottom(16)
        content.add(createComparisonMatrix(analysis), constraints)

        constraints.gridy++
        constraints.insets = JBUI.insets(4, 0, 8, 0)
        content.add(createSectionLabel("Conversion details"), constraints)

        constraints.gridy++
        constraints.insets = JBUI.insetsBottom(analysis.warnings.takeIf { it.isNotEmpty() }?.let { 14 } ?: 0)
        content.add(createDetailsPanel(analysis, resolutionBox), constraints)

        if (analysis.warnings.isNotEmpty()) {
            constraints.gridy++
            constraints.insets = JBUI.insets(4, 0, 8, 0)
            content.add(createSectionLabel("Attention"), constraints)

            constraints.gridy++
            constraints.insets = JBUI.emptyInsets()
            content.add(createWarningPanel(analysis.warnings), constraints)
        }

        val body = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(content, BorderLayout.NORTH)
            preferredSize = Dimension(700, preferredSize.height)
        }

        return JScrollPane(
            body,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
        ).apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            viewport.isOpaque = false
            verticalScrollBar.unitIncrement = JBUI.scale(16)
            val baseHeight = if (analysis.warnings.isNotEmpty()) 600 else 520
            preferredSize = Dimension(700, JBUI.scale(baseHeight))
        }
    }
}

internal class StorageConversionProgressDialog(
    private val project: Project,
    private val request: StorageConversionRequest,
    private val service: StatamicStorageConversionService,
    private val onFinished: (StorageConversionResult) -> Unit,
) : DialogWrapper(true) {
    private val titleLabel = JBLabel("Preparing conversion…").apply {
        font = UIManager.getFont("Label.font").deriveFont(Font.BOLD)
    }
    private val summaryLabel = JBLabel("Switching to ${request.target.displayName} storage").apply {
        foreground = UIUtil.getContextHelpForeground()
    }
    private val currentStepLabel = JBLabel("Inspecting current storage").apply {
        font = UIManager.getFont("Label.font").deriveFont(Font.BOLD, UIManager.getFont("Label.font").size2D - 0.5f)
    }
    private val percentLabel = JBLabel("0%").apply {
        foreground = UIUtil.getContextHelpForeground()
    }
    private val etaLabel = JBLabel(" ")
    private val bannerMessage = createWrappingText(
        "Backups and rollback checks are enabled for this conversion. Keep the IDE open until the process finishes.",
        columns = 68,
    )
    private val bannerPanel = JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
        border = bannerBorder(BannerTone.INFO)
        background = bannerBackground(BannerTone.INFO)
        isOpaque = true
        add(bannerMessage, BorderLayout.CENTER)
    }
    private val backupValueLabel = createPathCell("No backup created yet", secondary = true)
    private val logValueLabel = createPathCell("No log file yet", secondary = true)
    private val artifactsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        isVisible = false
        add(TitledSeparator("Artifacts"))
        add(verticalGap(8))
        add(createCompactDetailRow("Backup", backupValueLabel))
        add(verticalGap(6))
        add(createCompactDetailRow("Log", logValueLabel))
    }
    private val outputArea = JTextArea().apply {
        isEditable = false
        isOpaque = true
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        border = JBUI.Borders.empty()
        margin = JBUI.insets(10)
        background = consoleBackground()
        foreground = UIManager.getColor("Label.foreground")
    }
    private val progressBar = javax.swing.JProgressBar(0, 100).apply {
        value = 0
        isStringPainted = false
        setBorderPainted(false)
        foreground = progressFillColor()
        background = progressTrackColor()
        preferredSize = Dimension(JBUI.scale(320), JBUI.scale(4))
    }

    @Volatile
    private var running = true

    init {
        title = "Storage Conversion"
        isResizable = false
        init()
        window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowOpened(e: java.awt.event.WindowEvent) {
                window.removeWindowListener(this)
                startConversion()
            }
        })
    }

    override fun createActions() = arrayOf(okAction)

    override fun init() {
        super.init()
        okAction.isEnabled = false
        okAction.putValue(javax.swing.Action.NAME, "Close")
    }

    override fun doCancelAction() {
        if (!running) {
            super.doCancelAction()
        }
    }

    override fun createCenterPanel(): JComponent {
        val header = JPanel(GridBagLayout()).apply {
            isOpaque = false

            val constraints = GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                anchor = GridBagConstraints.NORTHWEST
                fill = GridBagConstraints.HORIZONTAL
            }

            constraints.gridy = 0
            add(titleLabel, constraints)

            constraints.gridy = 1
            constraints.insets = JBUI.insetsTop(2)
            add(summaryLabel, constraints)
        }

        val progressRow = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            isOpaque = false
            add(currentStepLabel, BorderLayout.WEST)
            add(percentLabel, BorderLayout.EAST)
        }

        val progressPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false

            val constraints = GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                anchor = GridBagConstraints.NORTHWEST
                fill = GridBagConstraints.HORIZONTAL
            }

            constraints.gridy = 0
            add(progressRow, constraints)

            constraints.gridy = 1
            constraints.insets = JBUI.insetsTop(8)
            add(progressBar, constraints)

            constraints.gridy = 2
            constraints.insets = JBUI.insetsTop(6)
            add(etaLabel, constraints)
        }

        val activityScrollPane = JScrollPane(outputArea).apply {
            preferredSize = Dimension(JBUI.scale(760), JBUI.scale(220))
            minimumSize = preferredSize
            border = consoleBorder()
            viewport.background = consoleBackground()
            viewport.isOpaque = true
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        val activityPanel = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            isOpaque = false
            add(TitledSeparator("Activity log"), BorderLayout.NORTH)
            add(activityScrollPane, BorderLayout.CENTER)
        }

        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(12, 16)
            isOpaque = false

            preferredSize = Dimension(JBUI.scale(860), JBUI.scale(430))
            minimumSize = preferredSize
            maximumSize = preferredSize

            val constraints = GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
            }

            constraints.gridy = 0
            constraints.insets = JBUI.insetsBottom(12)
            add(header, constraints)

            constraints.gridy = 1
            constraints.insets = JBUI.insetsBottom(14)
            add(bannerPanel, constraints)

            constraints.gridy = 2
            add(progressPanel, constraints)

            constraints.gridy = 3
            add(artifactsPanel, constraints)

            constraints.gridy = 4
            constraints.weighty = 1.0
            constraints.fill = GridBagConstraints.BOTH
            constraints.insets = JBUI.emptyInsets()
            add(activityPanel, constraints)
        }
    }

    private fun startConversion() {
        Thread {
            val result = service.convert(request) { progress ->
                SwingUtilities.invokeLater {
                    updateProgress(progress)
                }
            }

            running = false
            SwingUtilities.invokeLater {
                titleLabel.text = result.title
                summaryLabel.text = if (result.success) {
                    "The active storage driver was updated successfully."
                } else {
                    "The conversion stopped before the new storage driver could be activated."
                }
                currentStepLabel.text = if (result.success) {
                    "Review the verification summary below."
                } else {
                    "Review the error summary and activity log below."
                }
                percentLabel.text = "100%"
                etaLabel.text = " "
                progressBar.value = 100
                updateArtifacts(result)
                updateBanner(
                    tone = if (result.success) BannerTone.SUCCESS else BannerTone.ERROR,
                    message = result.message.lineSequence().firstOrNull().orEmpty().ifBlank {
                        if (result.success) "Conversion completed." else "Conversion failed."
                    },
                )
                appendOutput(result.message)
                if (result.verificationMessages.isNotEmpty()) {
                    appendOutput("")
                    appendOutput(if (result.success) "Verification:" else "Recovery:")
                    result.verificationMessages.forEach(::appendOutput)
                }
                okAction.isEnabled = true
                onFinished(result)
            }
        }.start()
    }

    private fun updateProgress(progress: com.antlers.support.statamic.StorageConversionProgress) {
        titleLabel.text = when (progress.phase) {
            StorageConversionPhase.ANALYZING -> "Analyzing conversion"
            StorageConversionPhase.VALIDATING -> "Validating requirements"
            StorageConversionPhase.BACKUP -> "Creating backups"
            StorageConversionPhase.MIGRATING -> "Migrating data"
            StorageConversionPhase.VERIFYING -> "Verifying data"
            StorageConversionPhase.FINALIZING -> "Finalizing driver switch"
            StorageConversionPhase.ROLLBACK -> "Rolling back changes"
            StorageConversionPhase.COMPLETED -> "Conversion complete"
        }
        summaryLabel.text = "Switching to ${request.target.displayName} storage"
        currentStepLabel.text = progress.currentOperation
        etaLabel.text = progress.eta ?: " "
        progressBar.value = progress.percent
        percentLabel.text = "${progress.percent}%"
        updateBanner(
            tone = BannerTone.INFO,
            message = progress.detail.ifBlank {
                "Backups and rollback checks are enabled for this conversion. Keep the IDE open until the process finishes."
            },
        )
        if (progress.detail.isNotBlank()) {
            appendOutput("${progress.currentOperation}: ${progress.detail}")
        } else {
            appendOutput(progress.currentOperation)
        }
    }

    private fun updateArtifacts(result: StorageConversionResult) {
        backupValueLabel.text = result.backupDirectory?.invariantSeparatorsPathString?.let { ellipsizeMiddle(it, 72) }
            ?: "No backup created"
        backupValueLabel.toolTipText = result.backupDirectory?.invariantSeparatorsPathString
        logValueLabel.text = result.logFile?.invariantSeparatorsPathString?.let { ellipsizeMiddle(it, 72) }
            ?: "No log file"
        logValueLabel.toolTipText = result.logFile?.invariantSeparatorsPathString
        artifactsPanel.isVisible = result.backupDirectory != null || result.logFile != null
        artifactsPanel.revalidate()
        artifactsPanel.repaint()
    }

    private fun updateBanner(
        tone: BannerTone,
        message: String,
    ) {
        bannerPanel.background = bannerBackground(tone)
        bannerPanel.border = bannerBorder(tone)
        bannerMessage.text = message
        bannerMessage.foreground = bannerForeground(tone)
        bannerPanel.repaint()
    }

    private fun appendOutput(line: String) {
        if (outputArea.text.isEmpty()) {
            outputArea.text = line
        } else {
            outputArea.append("\n$line")
        }
        outputArea.caretPosition = outputArea.document.length
    }
}

private enum class BannerTone {
    INFO,
    SUCCESS,
    ERROR,
}

private fun createCompactDetailRow(
    label: String,
    value: JComponent,
): JComponent {
    return JPanel(GridBagLayout()).apply {
        isOpaque = false

        val constraints = GridBagConstraints().apply {
            gridy = 0
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
        }

        constraints.gridx = 0
        constraints.weightx = 0.0
        constraints.insets = JBUI.insets(2, 0, 0, 12)
        add(
            JBLabel(label).apply {
                foreground = UIUtil.getContextHelpForeground()
                preferredSize = Dimension(JBUI.scale(54), preferredSize.height)
                minimumSize = preferredSize
            },
            constraints,
        )

        constraints.gridx = 1
        constraints.weightx = 1.0
        constraints.insets = JBUI.emptyInsets()
        add(value, constraints)
    }
}

private fun bannerBackground(tone: BannerTone): Color {
    return when (tone) {
        BannerTone.INFO -> JBColor(Color(0xF5F8FE), Color(0x25324D))
        BannerTone.SUCCESS -> JBColor(Color(0xEFF8F0), Color(0x21352A))
        BannerTone.ERROR -> JBColor(Color(0xFFF2F2), Color(0x4A2B31))
    }
}

private fun bannerForeground(tone: BannerTone): Color {
    return when (tone) {
        BannerTone.INFO -> UIManager.getColor("Label.foreground")
        BannerTone.SUCCESS -> UIManager.getColor("Label.foreground")
        BannerTone.ERROR -> UIManager.getColor("Label.foreground")
    }
}

private fun bannerBorder(tone: BannerTone) = BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(
        1,
        0,
        1,
        0,
        when (tone) {
            BannerTone.INFO -> JBColor(Color(0xC2D6FC), Color(0x36548C))
            BannerTone.SUCCESS -> JBColor(Color(0xB9E0BF), Color(0x355940))
            BannerTone.ERROR -> JBColor(Color(0xF0C2C2), Color(0x73424C))
        },
    ),
    JBUI.Borders.empty(10, 12),
)

private fun progressFillColor(): Color {
    return JBColor(Color(0x3574F0), Color(0x548AF7))
}

private fun progressTrackColor(): Color {
    return JBColor(Color(0xDFE1E5), Color(0x434952))
}

private fun consoleBackground(): Color {
    return JBColor(Color(0xFAFBFC), Color(0x2B2F34))
}

private fun consoleBorder() = BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(separatorColor()),
    JBUI.Borders.empty(),
)

private fun createWrappingText(
    text: String,
    secondary: Boolean = false,
    columns: Int = 24,
): JTextArea {
    return JTextArea(text).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        this.columns = columns
        border = JBUI.Borders.empty()
        font = UIManager.getFont("Label.font")
        alignmentX = JComponent.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        if (secondary) {
            foreground = UIUtil.getContextHelpForeground()
        }
    }
}

private fun createSectionLabel(text: String): JComponent {
    return TitledSeparator(text).apply {
        alignmentX = JComponent.LEFT_ALIGNMENT
    }
}

private fun createComparisonMatrix(analysis: StorageConversionAnalysis): JComponent {
    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(createComparisonHeaderRow())
        add(createComparisonDataRow("Driver", createDriverBadge(analysis.source, source = true), createDriverBadge(analysis.target, source = false)))
        add(createComparisonDataRow("Location", createPathCell(summarizedLocation(analysis.source)), createPathCell(summarizedLocation(analysis.target))))
        add(createComparisonDataRow("Detected size", createValueCell(formatStorageSize(analysis.source.sizeBytes)), createValueCell(formatStorageSize(analysis.target.sizeBytes))))
        add(createComparisonDataRow("Total records", createValueCell(formatRecordCount(analysis.source)), createValueCell(formatRecordCount(analysis.target)), showSeparator = false))
    }
}

private fun createComparisonHeaderRow(): JComponent {
    return createMatrixRow(
        attribute = createHeaderCell("Attribute"),
        current = createHeaderCell("Current (Source)"),
        target = createHeaderCell("Target"),
        addSeparator = true,
    )
}

private fun createComparisonDataRow(
    attribute: String,
    current: JComponent,
    target: JComponent,
    showSeparator: Boolean = true,
): JComponent {
    return createMatrixRow(
        attribute = createAttributeCell(attribute),
        current = current,
        target = target,
        addSeparator = showSeparator,
    )
}

private fun createMatrixRow(
    attribute: JComponent,
    current: JComponent,
    target: JComponent,
    addSeparator: Boolean,
): JComponent {
    return JPanel(GridBagLayout()).apply {
        isOpaque = false
        border = if (addSeparator) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, separatorColor()),
                JBUI.Borders.empty(10, 0)
            )
        } else {
            JBUI.Borders.empty(10, 0)
        }

        val constraints = GridBagConstraints().apply {
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            weighty = 0.0
            insets = JBUI.insets(0, 0, 0, 18)
        }

        constraints.gridx = 0
        constraints.weightx = 0.0
        add(createMatrixCell(attribute, JBUI.scale(145)), constraints)

        constraints.gridx = 1
        constraints.weightx = 0.5
        add(createMatrixCell(current, JBUI.scale(255)), constraints)

        constraints.gridx = 2
        constraints.weightx = 0.5
        constraints.insets = JBUI.emptyInsets()
        add(createMatrixCell(target, JBUI.scale(255)), constraints)
    }
}

private fun createDetailsPanel(
    analysis: StorageConversionAnalysis,
    resolutionBox: JComboBox<StorageConflictResolution>,
): JComponent {
    return JPanel(GridBagLayout()).apply {
        isOpaque = false

        val constraints = GridBagConstraints().apply {
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            weightx = 1.0
            insets = JBUI.insetsBottom(12)
        }

        add(
            createDetailRow(
                label = "Backup root",
                content = createValueStack(
                    primary = analysis.backupRoot.fileName?.toString() ?: analysis.backupRoot.invariantSeparatorsPathString,
                    note = analysis.backupRoot.invariantSeparatorsPathString,
                ),
            ),
            constraints,
        )

        constraints.gridy++
        add(
            createDetailRow(
                label = "Rollback strategy",
                content = createValueStack(
                    primary = "Automatic restore on failure",
                    note = "The previous file and database state will be restored if migration or verification fails.",
                ),
            ),
            constraints,
        )

        constraints.gridy++
        constraints.insets = JBUI.emptyInsets()
        add(
            createDetailRow(
                label = "Conflict handling",
                content = createConflictValue(analysis, resolutionBox),
            ),
            constraints,
        )
    }
}

private fun createDetailRow(
    label: String,
    content: JComponent,
): JComponent {
    return JPanel(GridBagLayout()).apply {
        isOpaque = false

        val constraints = GridBagConstraints().apply {
            gridy = 0
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
        }

        constraints.gridx = 0
        constraints.weightx = 0.0
        constraints.insets = JBUI.insets(2, 0, 0, 16)
        add(
            JBLabel(label).apply {
                foreground = UIUtil.getContextHelpForeground()
                preferredSize = Dimension(JBUI.scale(150), preferredSize.height)
                minimumSize = preferredSize
            },
            constraints,
        )

        constraints.gridx = 1
        constraints.weightx = 1.0
        constraints.insets = JBUI.emptyInsets()
        add(content, constraints)
    }
}

private fun createValueStack(
    primary: String,
    note: String? = null,
): JComponent {
    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(createWrappingText(primary, columns = 46))
        if (!note.isNullOrBlank()) {
            add(verticalGap(2))
            add(createPathCell(note, secondary = true))
        }
    }
}

private fun createConflictValue(
    analysis: StorageConversionAnalysis,
    resolutionBox: JComboBox<StorageConflictResolution>,
): JComponent {
    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        if (analysis.hasConflict) {
            add(resolutionBox)
            add(verticalGap(10))
            add(
                createWrappingText(
                    "The target already contains ${analysis.target.totalRecords} tracked record(s). Existing data will be handled according to the selected policy during migration.",
                    secondary = true,
                    columns = 46,
                )
            )
        } else {
            add(
                createWrappingText(
                    "No existing target data was detected. The conversion can proceed without merge handling.",
                    secondary = true,
                    columns = 46,
                )
            )
        }
    }
}

private fun createWarningPanel(warnings: List<String>): JComponent {
    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        warnings.forEachIndexed { index, warning ->
            if (index > 0) {
                add(verticalGap(8))
            }
            add(createWrappingText(warning, secondary = true))
        }
    }
}

private fun createHeaderCell(text: String): JComponent {
    return JBLabel(text).apply {
        font = UIManager.getFont("Label.font").deriveFont(Font.BOLD, UIManager.getFont("Label.font").size2D - 0.5f)
        foreground = UIUtil.getContextHelpForeground()
    }
}

private fun createAttributeCell(text: String): JComponent {
    return JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        verticalAlignment = javax.swing.SwingConstants.TOP
    }
}

private fun createValueCell(text: String): JComponent {
    return JBLabel(text).apply {
        foreground = UIManager.getColor("Label.foreground")
    }
}

private fun createPathCell(
    text: String,
    secondary: Boolean = false,
): JBLabel {
    val displayText = ellipsizeMiddle(text, if (secondary) 58 else 36)
    return JBLabel(displayText).apply {
        foreground = if (secondary) {
            UIUtil.getContextHelpForeground()
        } else {
            UIManager.getColor("Label.foreground")
        }
        toolTipText = if (displayText != text) text else null
    }
}

private fun createDriverBadge(
    snapshot: StorageSnapshot,
    source: Boolean,
): JComponent {
    val text = when (snapshot.driver) {
        StatamicDriver.FLAT_FILE -> "Flat-file (YAML)"
        StatamicDriver.ELOQUENT -> "Eloquent (${databaseFlavor(snapshot.locationDescription)})"
        StatamicDriver.UNKNOWN -> snapshot.driver.displayName()
    }
    val background = if (source) {
        SOURCE_BADGE_BACKGROUND
    } else {
        TARGET_BADGE_BACKGROUND
    }
    val foreground = if (source) {
        SOURCE_BADGE_FOREGROUND
    } else {
        TARGET_BADGE_FOREGROUND
    }

    return JPanel(BorderLayout()).apply {
        isOpaque = false
        add(StatusChip(text, background, foreground), BorderLayout.WEST)
    }
}

private fun createMatrixCell(
    content: JComponent,
    width: Int,
): JComponent {
    return JPanel(BorderLayout()).apply {
        isOpaque = false
        val contentSize = content.preferredSize
        preferredSize = Dimension(width, contentSize.height)
        minimumSize = Dimension(width, contentSize.height)
        maximumSize = Dimension(width, Int.MAX_VALUE)
        add(content, BorderLayout.WEST)
    }
}

private fun summarizedLocation(snapshot: StorageSnapshot): String {
    return when (snapshot.driver) {
        StatamicDriver.FLAT_FILE -> "content/, resources/, storage/"
        StatamicDriver.ELOQUENT,
        StatamicDriver.UNKNOWN,
        -> snapshot.locationDescription
    }
}

private fun formatRecordCount(snapshot: StorageSnapshot): String {
    val noun = when (snapshot.driver) {
        StatamicDriver.FLAT_FILE -> if (snapshot.totalRecords == 1) "file" else "files"
        StatamicDriver.ELOQUENT -> if (snapshot.totalRecords == 1) "record" else "records"
        StatamicDriver.UNKNOWN -> if (snapshot.totalRecords == 1) "item" else "items"
    }
    return "${snapshot.totalRecords} $noun"
}

private fun ellipsizeMiddle(
    text: String,
    maxLength: Int,
): String {
    if (text.length <= maxLength || maxLength < 7) {
        return text
    }

    val prefixLength = (maxLength - 1) / 2
    val suffixLength = maxLength - prefixLength - 1
    return text.take(prefixLength) + "…" + text.takeLast(suffixLength)
}

private fun databaseFlavor(locationDescription: String): String {
    return when {
        locationDescription.startsWith("mysql:") || locationDescription.startsWith("mariadb:") -> "MySQL"
        locationDescription.startsWith("pgsql:") -> "PostgreSQL"
        locationDescription.startsWith("sqlite:") -> "SQLite"
        else -> "database"
    }
}

private fun separatorColor(): Color {
    return JBColor(Color(0xDEE3EA), Color(0x434952))
}

private fun verticalGap(height: Int): JComponent {
    return JPanel().apply {
        isOpaque = false
        preferredSize = Dimension(1, JBUI.scale(height))
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(height))
    }
}

private class ResolutionListCellRenderer : javax.swing.DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: javax.swing.JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): java.awt.Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        text = when (value as? StorageConflictResolution) {
            StorageConflictResolution.MERGE -> "Merge with existing target data"
            StorageConflictResolution.OVERWRITE -> "Overwrite the existing target data"
            StorageConflictResolution.CANCEL -> "Cancel if target data exists"
            null -> ""
        }
        return component
    }
}

private class StatusChip(
    private val textValue: String,
    private val fillColor: Color,
    private val textColor: Color,
) : JBLabel(textValue) {
    init {
        font = UIManager.getFont("Label.font").deriveFont(Font.BOLD, UIManager.getFont("Label.font").size2D - 1f)
        foreground = textColor
        isOpaque = false
        border = JBUI.Borders.empty(3, 9)
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        return Dimension(size.width, maxOf(size.height, JBUI.scale(22)))
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize

    override fun paintComponent(graphics: Graphics) {
        val g2 = graphics.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = fillColor
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(12), JBUI.scale(12))
            super.paintComponent(g2)
        } finally {
            g2.dispose()
        }
    }
}

private val SOURCE_BADGE_BACKGROUND = JBColor(Color(0xECEFF3), Color(0x3C4045))
private val SOURCE_BADGE_FOREGROUND = JBColor(Color(0x4F5663), Color(0xD7DBE0))
private val TARGET_BADGE_BACKGROUND = JBColor(Color(0xE6F5EA), Color(0x274235))
private val TARGET_BADGE_FOREGROUND = JBColor(Color(0x2E6A46), Color(0x91D6AA))
