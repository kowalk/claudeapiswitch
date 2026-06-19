package com.github.kowalk.claudeapiswitch.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class ProviderStatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "claude-deepseek-switch.widget"

    override fun getDisplayName(): String = "Claude AI Provider"

    override fun isAvailable(project: Project): Boolean = true

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        ProviderStatusWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget as Disposable)
    }
}
