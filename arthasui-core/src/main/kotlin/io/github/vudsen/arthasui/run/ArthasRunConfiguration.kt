package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewWrapper
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

            override fun createConsole(executor: Executor): ConsoleView? {
                val console = super.createConsole(executor) ?: return null

                val displayName = state.displayName ?: state.jvm.name
                val banner = ConsoleCommandBanner(
                    project,
                    state.jvm,
                    state.tabId,
                    displayName
                )

                return BannerWrappedConsole(console, banner)
            }
        }
    }

    /**
     * ✅ 官方推荐方式：
     * - 继承 ConsoleViewWrapper
     * - 不碰 Console 行为
     * - 只改 UI
     */
    private class BannerWrappedConsole(
        delegate: ConsoleView,
        banner: JComponent
    ) : ConsoleViewWrapper(delegate) {

        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent(): JComponent = wrapperPanel

        override fun getPreferredFocusableComponent(): JComponent? =
            delegate.preferredFocusableComponent
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    override fun getState(): ArthasProcessOptions {
        return super.getState()
            ?: error("ArthasRunConfiguration state is missing")
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
