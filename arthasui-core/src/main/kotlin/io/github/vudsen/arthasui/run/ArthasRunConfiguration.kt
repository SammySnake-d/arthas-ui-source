package io.github.vudsen.arthasui.run

import com.intellij.execution.ui.ConsoleView
import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ExecutionConsole
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
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
                // 使用 getState() 获取配置，比直接用属性 state 更安全
                val config = this@ArthasRunConfiguration.getState()
                return ArthasProcessHandler(
                    project,
                    config.jvm,
                    config.tabId,
                    config.displayName
                )
            }

            override fun createConsole(executor: Executor): ExecutionConsole? {
                // ✅ 核心修复 1: 强转为 ConsoleView，如果父类返回的不是 ConsoleView 则无法包装
                val parentConsole = super.createConsole(executor) as? ConsoleView ?: return null

                val config = this@ArthasRunConfiguration.getState()
                val displayName = config.displayName ?: config.jvm.name

                // 创建自定义组件
                val banner = ConsoleCommandBanner(
                    project,
                    config.jvm,
                    config.tabId,
                    displayName
                )

                val operationPanel = ExecuteOperationPanel(
                    project,
                    config.jvm,
                    config.tabId
                )

                // ✅ 核心修复 2: 返回复合控制台
                return CompositeConsole(
                    delegate = parentConsole,
                    banner = banner,
                    operationPanel = operationPanel
                )
            }
        }
    }

    /**
     * ✅ 核心修复 3:
     * 1. 实现 ConsoleView 接口。
     * 2. 使用 'by delegate' 语法，自动将所有复杂的接口方法委托给 parentConsole。
     * 3. 这样你就不用手写 scrollTo, print 等几十个方法了，也不会报 "not abstract" 错误。
     */
    private class CompositeConsole(
        private val delegate: ConsoleView,
        banner: JPanel,
        operationPanel: JPanel
    ) : ConsoleView by delegate { // <--- 魔法在这里：所有方法自动委托给 delegate

        // 自定义布局：顶部 Banner，右侧操作板，中间原控制台
        private val rootPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(operationPanel, BorderLayout.EAST)
            add(delegate.component, BorderLayout.CENTER)
        }

        // 重写获取组件的方法，返回我们的组合面板
        override fun getComponent() = rootPanel

        // 焦点组件依然交给原控制台（保证快捷键、搜索正常）
        override fun getPreferredFocusableComponent() = delegate.preferredFocusableComponent

        // 销毁时连带销毁原控制台
        override fun dispose() {
            delegate.dispose()
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return ArthasSettingsEditor()
    }

    // 这里确保 RunConfigurationBase 正确实现了 PersistentStateComponent
    // 如果报 'overrides nothing'，请检查你的 IntelliJ SDK 版本是否支持此写法
    // 通常可以通过 getOptions() 替代
    override fun getState(): ArthasProcessOptions {
        return super.getState()!!
    }

    override fun createAdditionalTabComponents(
        manager: AdditionalTabComponentManager,
        startedProcess: ProcessHandler
    ) {
        if (manager is LogConsoleManagerBase) {
            manager.addAdditionalTabComponent(
                ExecuteHistoryUI(project, getState().jvm, getState().tabId),
                "io.github.vudsen.arthasui.run.ui.ExecuteHistoryUI",
                null,
                false
            )
        }
    }
}
