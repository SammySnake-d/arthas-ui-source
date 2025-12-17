package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ExecutionConsole
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
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
                
                return object : ExecutionConsole by console {
                    private val wrapperPanel = JPanel(BorderLayout()).apply {
                        add(banner, BorderLayout.NORTH)
                        add(console.component, BorderLayout.CENTER)
                    }
                    
                    override fun getComponent() = wrapperPanel
                }
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