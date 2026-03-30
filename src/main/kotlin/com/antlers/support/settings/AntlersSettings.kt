package com.antlers.support.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "AntlersSettings", storages = [Storage("AntlersSettings.xml")])
@Service(Service.Level.APP)
class AntlersSettings : PersistentStateComponent<AntlersSettings.State> {
    data class State(
        var enableAutoCloseDelimiters: Boolean = true,
        var enableAutoCloseQuotes: Boolean = true,
        var enableTagCompletion: Boolean = true,
        var enableModifierCompletion: Boolean = true,
        var enableVariableCompletion: Boolean = true,
        var enablePartialNavigation: Boolean = true,
        var enableHoverDocumentation: Boolean = true,
        var enableAlpineJsInjection: Boolean = true,
        var enablePhpInjection: Boolean = true,
        var enableSemanticHighlighting: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): AntlersSettings =
            ApplicationManager.getApplication().getService(AntlersSettings::class.java)
    }
}
