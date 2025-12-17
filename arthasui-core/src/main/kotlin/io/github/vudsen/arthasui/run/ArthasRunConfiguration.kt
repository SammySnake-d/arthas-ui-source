package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.AdditionalTabComponentManager // [修复] 添加缺失的 import
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer // [建议] 使用 Disposer 管理层级
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
                // 如果父类返回 null，直接返回
                val console = super.createConsole(executor) ?: return null

                val displayName = state.displayName ?: state.jvm.name
                val banner = ConsoleCommandBanner(project, state.jvm, state.tabId, displayName)

                // 使用修正后的 Wrapper 类
                return BannerWrappedConsole(console, banner)
            }
        }
    }

    /**
     * Wrapper class for ConsoleView that adds a banner at the top.
     * 
     * [关键修复] 使用 Kotlin 的 "Implementation by Delegation" (by delegate)。
     * 这样无需手动实现 print, clear, scrollTo, requestScrollToCaret 等所有方法，
     * 编译器会自动生成代理代码。我们只需重写 getComponent 和 dispose 即可。
     */
    private class BannerWrappedConsole(
        private val delegate: ConsoleView,
        private val banner: ConsoleCommandBanner
    ) : ConsoleView by delegate, Disposable { // <--- 这里的 'by delegate' 解决了所有接口缺失问题

        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent(): JComponent = wrapperPanel

        override fun getPreferredFocusableComponent(): JComponent = delegate.preferredFocusableComponent

        override fun dispose() {
            // 清理 Banner 资源（如果 Banner 实现了 Disposable，最好也 dispose 它）
            if (banner is Disposable) {
                Disposer.dispose(banner)
            }
            // 清理被包装的 Console
            delegate.dispose()
        }
        
        // 不需要再手动写 requestScrollToCaret, print 等方法了，
        // 'by delegate' 已经自动处理了。
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    // 这一步通常在基类处理了，如果不需要特殊逻辑可以移除，或者保留默认
    override fun createRunnerSettings(provider: ConfigurationInfoProvider?): ConfigurationPerRunnerSettings? {
        return super.createRunnerSettings(provider)
    }

    // 确保 options 不为空
    override fun getState(): ArthasProcessOptions {
        return super.getState() ?: throw ExecutionException("Run configuration state is missing")
    }

    override fun createAdditionalTabComponents(
        manager: AdditionalTabComponentManager,
        startedProcess: ProcessHandler
    ) {
        // LogConsoleManagerBase 在新版 IDE 中可能会有变化，注意兼容性
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
