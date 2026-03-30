package com.antlers.support.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AntlersSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var autoCloseDelimiters: JBCheckBox? = null
    private var autoCloseQuotes: JBCheckBox? = null
    private var tagCompletion: JBCheckBox? = null
    private var modifierCompletion: JBCheckBox? = null
    private var variableCompletion: JBCheckBox? = null
    private var partialNavigation: JBCheckBox? = null
    private var hoverDocumentation: JBCheckBox? = null
    private var alpineJsInjection: JBCheckBox? = null
    private var phpInjection: JBCheckBox? = null
    private var semanticHighlighting: JBCheckBox? = null

    override fun getDisplayName(): String = "Statamic Toolkit"

    override fun createComponent(): JComponent {
        autoCloseDelimiters = JBCheckBox("Auto-close {{ }} delimiters")
        autoCloseQuotes = JBCheckBox("Auto-close quotes inside expressions")
        tagCompletion = JBCheckBox("Tag name completion")
        modifierCompletion = JBCheckBox("Modifier completion (after |)")
        variableCompletion = JBCheckBox("Variable completion")
        partialNavigation = JBCheckBox("Cmd+click navigation to partials")
        hoverDocumentation = JBCheckBox("Show Statamic documentation on hover")
        alpineJsInjection = JBCheckBox("Alpine.js intelligence in Antlers templates")
        phpInjection = JBCheckBox("PHP intelligence in {{? ?}} and {{$ $}} blocks")
        semanticHighlighting = JBCheckBox("Semantic highlighting for tag names and parameters")

        panel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Editor"))
            .addComponent(autoCloseDelimiters!!)
            .addComponent(autoCloseQuotes!!)
            .addComponent(semanticHighlighting!!)
            .addComponent(TitledSeparator("Completion"))
            .addComponent(tagCompletion!!)
            .addComponent(modifierCompletion!!)
            .addComponent(variableCompletion!!)
            .addComponent(TitledSeparator("Navigation & Documentation"))
            .addComponent(partialNavigation!!)
            .addComponent(hoverDocumentation!!)
            .addComponent(TitledSeparator("Language Injection"))
            .addComponent(phpInjection!!)
            .addComponent(alpineJsInjection!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = AntlersSettings.getInstance().state
        return autoCloseDelimiters?.isSelected != settings.enableAutoCloseDelimiters
            || autoCloseQuotes?.isSelected != settings.enableAutoCloseQuotes
            || tagCompletion?.isSelected != settings.enableTagCompletion
            || modifierCompletion?.isSelected != settings.enableModifierCompletion
            || variableCompletion?.isSelected != settings.enableVariableCompletion
            || partialNavigation?.isSelected != settings.enablePartialNavigation
            || hoverDocumentation?.isSelected != settings.enableHoverDocumentation
            || alpineJsInjection?.isSelected != settings.enableAlpineJsInjection
            || phpInjection?.isSelected != settings.enablePhpInjection
            || semanticHighlighting?.isSelected != settings.enableSemanticHighlighting
    }

    override fun apply() {
        val settings = AntlersSettings.getInstance().state
        settings.enableAutoCloseDelimiters = autoCloseDelimiters?.isSelected ?: true
        settings.enableAutoCloseQuotes = autoCloseQuotes?.isSelected ?: true
        settings.enableTagCompletion = tagCompletion?.isSelected ?: true
        settings.enableModifierCompletion = modifierCompletion?.isSelected ?: true
        settings.enableVariableCompletion = variableCompletion?.isSelected ?: true
        settings.enablePartialNavigation = partialNavigation?.isSelected ?: true
        settings.enableHoverDocumentation = hoverDocumentation?.isSelected ?: true
        settings.enableAlpineJsInjection = alpineJsInjection?.isSelected ?: true
        settings.enablePhpInjection = phpInjection?.isSelected ?: true
        settings.enableSemanticHighlighting = semanticHighlighting?.isSelected ?: true
    }

    override fun reset() {
        val settings = AntlersSettings.getInstance().state
        autoCloseDelimiters?.isSelected = settings.enableAutoCloseDelimiters
        autoCloseQuotes?.isSelected = settings.enableAutoCloseQuotes
        tagCompletion?.isSelected = settings.enableTagCompletion
        modifierCompletion?.isSelected = settings.enableModifierCompletion
        variableCompletion?.isSelected = settings.enableVariableCompletion
        partialNavigation?.isSelected = settings.enablePartialNavigation
        hoverDocumentation?.isSelected = settings.enableHoverDocumentation
        alpineJsInjection?.isSelected = settings.enableAlpineJsInjection
        phpInjection?.isSelected = settings.enablePhpInjection
        semanticHighlighting?.isSelected = settings.enableSemanticHighlighting
    }
}
