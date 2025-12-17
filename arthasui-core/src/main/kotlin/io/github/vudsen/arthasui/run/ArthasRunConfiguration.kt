package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import io.github.vudsen.arthasui.api.JVM
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
            
            override fun createConsole(executor: Executor): ExecutionConsole? {
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
     * Wrapper class for ExecutionConsole that adds a banner at the top.
     * 包装器类，在控制台顶部添加横幅。
     */
    private class BannerWrappedConsole(
        private val delegate: ExecutionConsole,
        banner: ConsoleCommandBanner
    ) : ExecutionConsole, Disposable {
        
        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }
        
        override fun getComponent() = wrapperPanel
        
        override fun getPreferredFocusableComponent() = delegate.preferredFocusableComponent
        
        override fun dispose() {
            if (delegate is Disposable) {
                delegate.dispose()
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

}
