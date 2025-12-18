package io.github.vudsen.arthasui.run

import com.intellij.diagnostic.logging.LogConsoleManagerBase
import com.intellij.execution.ExecutionConsole
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView // ✅ 必须引入这个接口
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
                // 注意：这里使用 this@ArthasRunConfiguration.state 来访问配置数据
                val config = this@ArthasRunConfiguration.state
                return ArthasProcessHandler(
                    project,
                    config.jvm,
                    config.tabId,
                    config.displayName
                )
            }

            override fun createConsole(executor: Executor): ExecutionConsole? {
                // ✅ 关键点1：创建父类控制台，并强转为 ConsoleView。
                // 如果父类返回的不是 ConsoleView（极少情况），则无法进行包装。
                val console = super.createConsole(executor) as? ConsoleView ?: return null

                val config = this@ArthasRunConfiguration.state
                val displayName = config.displayName ?: config.jvm.name

                // 创建你的自定义组件
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

                // ✅ 关键点2：返回使用了“委托模式”的复合控制台
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
     * 
     * ✅ 关键修复：
     * 1. 继承 ConsoleView 接口（保证 IDE 能识别这是个可输出日志的控制台）。
     * 2. 使用 'by delegate' 语法，将所有复杂的接口方法自动委托给原控制台实现。
     * 3. 仅重写 getComponent() 来改变 UI 布局。
     */
    private class CompositeConsole(
        private val delegate: ConsoleView,
        banner: JPanel,
        operationPanel: JPanel
    ) : ConsoleView by delegate { 

        // 自定义布局：北边 Banner，东边按钮，中间原控制台
        private val rootPanel = JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(operationPanel, BorderLayout.EAST)
            add(delegate.component, BorderLayout.CENTER)
        }

        override fun getComponent() = rootPanel

        // 焦点控制交给原控制台（保证快捷键、搜索等功能正常）
        override fun getPreferredFocusableComponent() = delegate.preferredFocusableComponent

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
