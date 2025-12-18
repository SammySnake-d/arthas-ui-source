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
import io.github.vudsen.arthasui.run.ui.ExecuteOperationPanel
import java.awt.BorderLayout
import javax.swing.JPanel

class ArthasRunConfiguration(
    project: Project,
    configurationFactory: ConfigurationFactory
) : RunConfigurationBase<ArthasProcessOptions>(
    project,
    configurationFactory,
    "Arthas Query Console"
) {

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState {

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

                val displayName = state.displayName ?: state.jvm.name

                val banner = ConsoleCommandBanner(
                    project,
                    state.jvm,
                    state.tabId,
                    displayName
                )

                val operationPanel = ExecuteOperationPanel(
                    project,
                    state.jvm,
                    state.tabId
                )

                return CompositeConsole(
                    delegate = console,
                    banner = banner,
                    operationPanel = operationPanel
                )
            }
        }
    }

    /**
     * 顶部 Banner + 右侧操作区 + Console 的复合控制台
     */
    private class CompositeConsole(
        private val delegate: ExecutionConsole,
        banner: JPanel,
        operationPanel: JPanel
    ) : ExecutionConsole {

        private val rootPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(operationPanel, BorderLayout.EAST)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent() = rootPanel

        override fun getPreferredFocusableComponent() =
            delegate.preferredFocusableComponent

        override fun dispose() {
            delegate.dispose()
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    override fun getState(): ArthasProcessOptions {
        return super.getState()!!
    }

    override fun createAdditionalTabComponents(
        manager: AdditionalTabComponentManager,
        startedProcess: ProcessHandler
    ) {
        if (manager is LogConsoleManagerBase) {
            manager.addAdditionalTabComponent(
                ExecuteHistoryUI(project, state.jvm, state.tabId),
                "io.github.vudsen.arthasui.run.ui.ExecuteHistoryUI",
                null,
                false
            )
        }
    }
}
