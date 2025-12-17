package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.github.vudsen.arthasui.run.ui.ConsoleCommandBanner
import io.github.vudsen.arthasui.run.ui.ExecuteHistoryUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ArthasRunConfiguration(
    project: Project,
    configurationFactory: ConfigurationFactory
) : RunConfigurationBase<ArthasProcessOptions>(project, configurationFactory, "Arthas Query Console") {

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

                val displayName = state.displayName ?: state.jvm.name
                val banner = ConsoleCommandBanner(project, state.jvm, state.tabId, displayName)

                return BannerWrappedConsole(console, banner)
            }
        }
    }

    /**
     * Wrapper class for ConsoleView that adds a banner at the top.
     * 使用 Kotlin 类委托 (by delegate) 自动实现 ConsoleView 的所有方法
     */
    private class BannerWrappedConsole(
        private val delegate: ConsoleView,
        private val banner: ConsoleCommandBanner
    ) : ConsoleView by delegate, Disposable {

        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent(): JComponent = wrapperPanel

        override fun getPreferredFocusableComponent(): JComponent = delegate.preferredFocusableComponent

        override fun dispose() {
            // 安全清理 banner
            if (banner is Disposable) {
                Disposer.dispose(banner)
            }
            // 清理被代理的 console
            delegate.dispose()
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    override fun createRunnerSettings(provider: ConfigurationInfoProvider?): ConfigurationPerRunnerSettings? {
        return super.createRunnerSettings(provider)
    }

    override fun getState(): ArthasProcessOptions {
        return super.getState() ?: throw ExecutionException("Run configuration state is missing")
    }

    // [已删除] createAdditionalTabComponents 方法。
    // 该方法依赖的 AdditionalTabComponentManager 在新版 SDK 中已删除。
    // 如果需要添加 History Tab，新版本通常通过 RunConfigurationExtension 
    // 或者在 LogConsoleManagerBase 的其他生命周期中处理，但在基础修复中必须先删除以通过编译。
}
