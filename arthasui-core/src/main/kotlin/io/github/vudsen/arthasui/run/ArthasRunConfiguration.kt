package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.vudsen.arthasui.run.ui.ConsoleCommandBanner
import io.github.vudsen.arthasui.run.ui.ExecuteHistoryUI
import java.awt.BorderLayout
import javax.swing.JPanel

class ArthasRunConfiguration(
    project: Project,
    configurationFactory: ConfigurationFactory
) :
    RunConfigurationBase<ArthasProcessOptions>(project, configurationFactory, "Arthas Query Console") {

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                return ArthasProcessHandler(
                    project,
                    state.jvm,
                    state.tabId,
                    state.displayName
                )
            }
            
            @Throws(ExecutionException::class)
            override fun createConsole(executor: Executor): ConsoleView? {
                val console = super.createConsole(executor) ?: return null
                
                // Create a wrapper panel with the banner at the top
                // 创建一个包含横幅的包装面板，横幅显示在控制台上方
                val displayName = state.displayName ?: state.jvm.name
                val banner = ConsoleCommandBanner(project, state.jvm, state.tabId, displayName)
                
                return BannerWrappedConsole(console, banner)
            }
        }
    }
    
    /**
     * Wrapper class for ConsoleView that adds a banner at the top.
     * 包装器类，在控制台顶部添加横幅。
     */
    private class BannerWrappedConsole(
        private val delegate: ConsoleView,
        private val banner: ConsoleCommandBanner
    ) : ConsoleView, Disposable {
        
        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }
        
        override fun getComponent() = wrapperPanel
        
        override fun getPreferredFocusableComponent() = delegate.preferredFocusableComponent
        
        override fun dispose() {
            delegate.dispose()
        }
        
        // ConsoleView interface methods - delegate to the wrapped console
        override fun print(text: String, contentType: ConsoleViewContentType) {
            delegate.print(text, contentType)
        }
        
        override fun clear() {
            delegate.clear()
        }
        
        override fun scrollTo(offset: Int) {
            delegate.scrollTo(offset)
        }
        
        override fun attachToProcess(processHandler: ProcessHandler) {
            delegate.attachToProcess(processHandler)
        }
        
        override fun setOutputPaused(value: Boolean) {
            delegate.setOutputPaused(value)
        }
        
        override fun isOutputPaused(): Boolean {
            return delegate.isOutputPaused
        }
        
        override fun hasDeferredOutput(): Boolean {
            return delegate.hasDeferredOutput()
        }
        
        override fun performWhenNoDeferredOutput(runnable: Runnable) {
            delegate.performWhenNoDeferredOutput(runnable)
        }
        
        override fun setHelpId(helpId: String) {
            delegate.setHelpId(helpId)
        }
        
        override fun addMessageFilter(filter: Filter) {
            delegate.addMessageFilter(filter)
        }
        
        override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) {
            delegate.printHyperlink(hyperlinkText, info)
        }
        
        override fun getContentSize(): Int {
            return delegate.contentSize
        }
        
        override fun canPause(): Boolean {
            return delegate.canPause()
        }
        
        override fun createConsoleActions(): Array<AnAction> {
            return delegate.createConsoleActions()
        }
        
        override fun allowHeavyFilters() {
            delegate.allowHeavyFilters()
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

}
