package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
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
             * ⚠️ 新 SDK 下只能返回 ConsoleView
             * 但我们只包 UI，不做代理
             */
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
     * ⚠️ 关键点：
     * - 实现 ConsoleView
     * - 不使用 by delegate
     * - 所有行为直接转发
     */
    private class BannerWrappedConsole(
        private val delegate: ConsoleView,
        private val banner: JComponent
    ) : ConsoleView {

        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent(): JComponent = wrapperPanel

        override fun getPreferredFocusableComponent(): JComponent? =
            delegate.preferredFocusableComponent

        // === 行为完全转发，保证 Console 正常工作 ===

        override fun print(text: String, contentType: com.intellij.execution.ui.ConsoleViewContentType) {
            delegate.print(text, contentType)
        }

        override fun clear() {
            delegate.clear()
        }

        override fun scrollToEnd() {
            delegate.scrollToEnd()
        }

        override fun attachToProcess(processHandler: ProcessHandler) {
            delegate.attachToProcess(processHandler)
        }

        override fun setOutputPaused(value: Boolean) {
            delegate.isOutputPaused = value
        }

        override fun isOutputPaused(): Boolean =
            delegate.isOutputPaused

        override fun hasDeferredOutput(): Boolean =
            delegate.hasDeferredOutput()

        override fun performWhenNoDeferredOutput(runnable: Runnable) {
            delegate.performWhenNoDeferredOutput(runnable)
        }

        override fun setHelpId(helpId: String) {
            delegate.setHelpId(helpId)
        }

        override fun addMessageFilter(filter: com.intellij.execution.filters.Filter) {
            delegate.addMessageFilter(filter)
        }

        override fun printHyperlink(text: String, info: com.intellij.execution.filters.HyperlinkInfo?) {
            delegate.printHyperlink(text, info)
        }

        override fun dispose() {
            delegate.dispose()
        }
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
