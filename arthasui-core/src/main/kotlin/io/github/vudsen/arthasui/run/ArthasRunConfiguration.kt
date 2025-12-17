package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ExecutionConsole
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
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

            /**
             * ⚠️ 关键点：
             * - 返回 ExecutionConsole
             * - 不改变 Console 类型语义
             */
            override fun createConsole(executor: Executor): ExecutionConsole? {
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
     * UI 包装器（只包 UI，不干预 Console 行为）
     */
    private class BannerWrappedConsole(
        private val delegate: ExecutionConsole,
        private val banner: JComponent
    ) : ExecutionConsole, Disposable {

        private val wrapperPanel: JPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent(): JComponent = wrapperPanel

        override fun getPreferredFocusableComponent(): JComponent? =
            delegate.preferredFocusableComponent

        /**
         * ⚠️ 不要额外 dispose delegate
         * IDE 会统一管理 Console 生命周期
         */
        override fun dispose() {
            delegate.dispose()
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    override fun createRunnerSettings(
        provider: ConfigurationInfoProvider?
    ): ConfigurationPerRunnerSettings? {
        return super.createRunnerSettings(provider)
    }

    override fun getState(): ArthasProcessOptions {
        return super.getState()
            ?: error("ArthasRunConfiguration state is missing")
    }

    /**
     * 追加 History Tab（这是 A 中本来就可行的逻辑）
     */
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
