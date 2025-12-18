package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewPlace
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.vudsen.arthasui.run.ui.ConsoleCommandBanner
import io.github.vudsen.arthasui.run.ui.ExecuteHistoryUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ArthasRunConfiguration(
    project: Project,
    configurationFactory: ConfigurationFactory
) :
    RunConfigurationBase<ArthasProcessOptions>(project, configurationFactory, "Arthas Query Console") {

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val options = state
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                return ArthasProcessHandler(
                    project,
                    options.jvm,
                    options.tabId,
                    options.displayName
                )
            }
            
            override fun createConsole(executor: Executor): ConsoleView? {
                val console = super.createConsole(executor) ?: return null
                val displayName = options.displayName ?: options.jvm.name
                val banner = ConsoleCommandBanner(project, options.jvm, options.tabId, displayName)
                return BannerWrappedConsole(console, banner)
            }
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    override fun createRunnerSettings(provider: ConfigurationInfoProvider?): ConfigurationPerRunnerSettings? {
        return super.createRunnerSettings(provider)
    }

    override fun getState(): ArthasProcessOptions {
        return super.getState()!!
    }

    override fun createAdditionalTabComponents(
        manager: AdditionalTabComponentManager,
        startedProcess: ProcessHandler
    ) {
        if (manager is LogConsoleManagerBase) {
            manager.addAdditionalTabComponent(ExecuteHistoryUI(project, state.jvm, state.tabId), "io.github.vudsen.arthasui.run.ui.ExecuteHistoryUI", null, false)
        }
    }
    
    /**
     * 包装 ConsoleView，在顶部添加横幅
     */
    private class BannerWrappedConsole(
        private val delegate: ConsoleView,
        private val banner: ConsoleCommandBanner
    ) : ConsoleView {
        
        private val wrapperPanel: JPanel by lazy {
            JPanel(BorderLayout()).apply {
                add(banner, BorderLayout.NORTH)
                add(delegate.component, BorderLayout.CENTER)
            }
        }
        
        // === ComponentContainer ===
        override fun getComponent(): JComponent = wrapperPanel
        override fun getPreferredFocusableComponent(): JComponent = delegate.preferredFocusableComponent
        
        // === Disposable ===
        override fun dispose() = delegate.dispose()
        
        // === ConsoleView ===
        override fun print(text: String, contentType: ConsoleViewContentType) = delegate.print(text, contentType)
        override fun clear() = delegate.clear()
        override fun scrollTo(offset: Int) = delegate.scrollTo(offset)
        override fun attachToProcess(processHandler: ProcessHandler) = delegate.attachToProcess(processHandler)
        override fun setOutputPaused(value: Boolean) { delegate.isOutputPaused = value }
        override fun isOutputPaused(): Boolean = delegate.isOutputPaused
        override fun hasDeferredOutput(): Boolean = delegate.hasDeferredOutput()
        override fun performWhenNoDeferredOutput(runnable: Runnable) = delegate.performWhenNoDeferredOutput(runnable)
        override fun setHelpId(helpId: String) = delegate.setHelpId(helpId)
        override fun addMessageFilter(filter: Filter) = delegate.addMessageFilter(filter)
        override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) = delegate.printHyperlink(hyperlinkText, info)
        override fun getContentSize(): Int = delegate.contentSize
        override fun canPause(): Boolean = delegate.canPause()
        override fun createConsoleActions(): Array<AnAction> = delegate.createConsoleActions()
        override fun allowHeavyFilters() = delegate.allowHeavyFilters()
        override fun getPlace(): ConsoleViewPlace = delegate.place
        override fun requestScrollingToEnd() = delegate.requestScrollingToEnd()
    }
}
