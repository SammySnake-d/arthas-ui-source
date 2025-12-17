package io.github.vudsen.arthasui.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider // 必须导入这个
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.github.vudsen.arthasui.run.ui.ConsoleCommandBanner
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
                
                // 返回修复后的 Wrapper
                return BannerWrappedConsole(console, banner)
            }
        }
    }

    /**
     * 修复版 Wrapper：
     * 1. 实现了 ConsoleView (满足新版接口)
     * 2. 实现了 DataProvider (满足 IDE 数据查询，解决控制台不显示的问题)
     */
    private class BannerWrappedConsole(
        private val delegate: ConsoleView,
        private val banner: ConsoleCommandBanner
    ) : ConsoleView by delegate, DataProvider, Disposable { // <--- 关键：加上 DataProvider

        private val wrapperPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent(): JComponent = wrapperPanel
        override fun getPreferredFocusableComponent(): JComponent = delegate.preferredFocusableComponent

        override fun dispose() {
            if (banner is Disposable) Disposer.dispose(banner)
            delegate.dispose()
        }

        // <--- 核心修复：把数据查询转发给内部的 console
        override fun getData(dataId: String): Any? {
            if (delegate is DataProvider) {
                return (delegate as DataProvider).getData(dataId)
            }
            return null
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
    
    // createAdditionalTabComponents 已删除
}
