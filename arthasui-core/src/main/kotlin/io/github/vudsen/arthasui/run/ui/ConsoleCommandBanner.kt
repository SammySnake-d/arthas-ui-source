package io.github.vudsen.arthasui.run.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.vudsen.arthasui.api.*
import io.github.vudsen.arthasui.language.arthas.psi.ArthasFileType
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 控制台命令信息横幅面板。
 * 
 * 显示当前执行命令的解析信息（命令类型、类名、方法名、OGNL表达式等），
 * 以"描述: 值"的格式展示在控制台上方。
 */
class ConsoleCommandBanner(
    private val project: Project,
    private val jvm: JVM,
    private val tabId: String?,
    private val editorFileName: String
) : JPanel(BorderLayout()) {

    private val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)))
    private val waitingLabel = JLabel("等待执行命令...", SwingConstants.CENTER)
    
    private var listenerRegistered = false

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8))
        )
        background = JBColor(0xF7F7F7, 0x3C3F41)

        add(infoPanel, BorderLayout.CENTER)
        
        // 右侧按钮面板：终止按钮 + 跳转按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(createStopButton())
            add(createNavigateButton())
        }
        add(buttonPanel, BorderLayout.EAST)
        
        showWaiting()
        
        // 延迟注册监听器，避免在初始化时阻塞
        SwingUtilities.invokeLater { tryRegisterListener() }
    }
    
    /**
     * 尝试注册监听器到 template
     */
    private fun tryRegisterListener() {
        if (listenerRegistered) return
        
        val template = service<ArthasExecutionManager>().getTemplate(jvm, tabId) ?: return
        
        listenerRegistered = true
        
        var lastDisplayedCommand: String? = null
        
        template.addListener(object : ArthasBridgeListener() {
            override fun onContent(result: String) {
                // 当收到内容时，检查是否有新命令正在执行
                val currentCommand = service<ArthasExecutionManager>().getCurrentCommand(jvm, tabId)
                if (currentCommand != null && currentCommand != lastDisplayedCommand) {
                    lastDisplayedCommand = currentCommand
                    ApplicationManager.getApplication().invokeLater {
                        updateDisplay(currentCommand, isError = false, isRunning = true)
                    }
                }
            }
            
            override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                lastDisplayedCommand = command
                ApplicationManager.getApplication().invokeLater {
                    updateDisplay(command, isError = false, isRunning = false)
                }
            }

            override fun onError(command: String, rawContent: String, exception: Exception) {
                lastDisplayedCommand = command
                ApplicationManager.getApplication().invokeLater {
                    updateDisplay(command, isError = true, isRunning = false)
                }
            }
        })
    }
    
    private fun showWaiting() {
        infoPanel.removeAll()
        infoPanel.add(waitingLabel)
        infoPanel.revalidate()
        infoPanel.repaint()
    }
    
    /**
     * 创建终止按钮 - 使用 IntelliJ 官方的停止图标
     */
    private fun createStopButton(): JLabel {
        return JLabel(AllIcons.Actions.Suspend).apply {
            toolTipText = "终止当前命令"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(4), JBUI.scale(2), JBUI.scale(4))
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    stopCurrentCommand()
                }
            })
        }
    }
    
    /**
     * 终止当前正在执行的命令
     * 通过取消 ProgressIndicator 来中断命令执行，效果与点击后台任务条的取消按钮相同
     */
    private fun stopCurrentCommand() {
        service<ArthasExecutionManager>().cancelCurrentCommand(jvm, tabId)
    }
    
    /**
     * 创建跳转按钮
     */
    private fun createNavigateButton(): JLabel {
        return JLabel("跳转到编辑器", AllIcons.Actions.EditSource, SwingConstants.LEFT).apply {
            toolTipText = "点击跳转到查询编辑器"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(6), JBUI.scale(2), JBUI.scale(6))
            )
            background = JBColor(0xE8E8E8, 0x4A4A4A)
            isOpaque = true
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    navigateToQueryEditor()
                }
                
                override fun mouseEntered(e: MouseEvent) {
                    background = JBColor(0xD8D8D8, 0x555555)
                }
                
                override fun mouseExited(e: MouseEvent) {
                    background = JBColor(0xE8E8E8, 0x4A4A4A)
                }
            })
        }
    }
    
    /**
     * 跳转到对应的查询编辑器
     */
    private fun navigateToQueryEditor() {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.allEditors.find { editor -> 
            editor.file.fileType == ArthasFileType && editor.file.name == editorFileName
        }?.let { matchedEditor ->
            fileEditorManager.openFile(matchedEditor.file, true, true)
        }
    }

    /**
     * 更新横幅显示内容
     * @param command 命令字符串
     * @param isError 是否出错
     * @param isRunning 是否正在运行
     */
    private fun updateDisplay(command: String?, isError: Boolean = false, isRunning: Boolean = false) {
        infoPanel.removeAll()

        if (command.isNullOrBlank()) {
            showWaiting()
            return
        }

        val commandInfo = parseCommand(command)
        
        // 添加状态标签
        val statusText = when {
            isError -> "错误"
            isRunning -> "运行中"
            else -> "已完成"
        }
        val statusLabel = createTag("状态", statusText)
        when {
            isError -> statusLabel.foreground = JBColor.RED
            isRunning -> statusLabel.foreground = JBColor(0x59A869, 0x499C54)  // 绿色
        }
        infoPanel.add(statusLabel)
        
        // 添加解析出的命令参数
        commandInfo.forEach { (key, value) ->
            if (value.isNotBlank()) {
                infoPanel.add(createTag(key, value))
            }
        }

        infoPanel.revalidate()
        infoPanel.repaint()
    }

    /**
     * 创建标签组件
     */
    private fun createTag(key: String, value: String): JLabel {
        return JLabel("$key: $value").apply {
            foreground = JBColor(0x333333, 0xBBBBBB)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(6), JBUI.scale(2), JBUI.scale(6))
            )
            background = JBColor(0xFFFFFF, 0x2B2B2B)
            isOpaque = true
        }
    }

    /**
     * 解析 Arthas 命令
     */
    private fun parseCommand(command: String): Map<String, String> {
        val info = linkedMapOf<String, String>()
        // 移除命令末尾的分号
        val cleanCommand = command.trim().removeSuffix(";").trim()
        val parts = cleanCommand.split(Regex("\\s+"))
        
        if (parts.isEmpty()) return info
        
        val commandName = parts[0].lowercase()
        info["命令"] = parts[0]
        
        when (commandName) {
            "watch" -> parseClassMethodCommand(parts, info, hasOgnl = true)
            "trace", "stack", "monitor", "tt" -> parseClassMethodCommand(parts, info)
            "jad", "sc", "dump", "classloader", "memory", "heapdump" -> parseClassOnlyCommand(parts, info)
            "sm" -> parseClassMethodCommand(parts, info)
            "ognl" -> parseOgnlCommand(parts, info)
            "getstatic" -> parseGetstaticCommand(parts, info)
            "thread" -> parseThreadCommand(parts, info)
            "vmtool" -> parseVmtoolCommand(parts, info)
            // 简单命令（无参数或可选参数）- 只显示命令名
            "dashboard", "jvm", "sysprop", "sysenv", "vmoption", "perfcounter", 
            "logger", "mbean", "version", "session", "reset", "shutdown", "stop",
            "help", "cat", "base64", "tee", "pwd", "cls", "history", "quit", "exit", "keymap" -> {
                // 这些命令只需要显示命令名，已经在上面添加了
            }
        }
        
        return info
    }

    private fun parseClassMethodCommand(parts: List<String>, info: MutableMap<String, String>, hasOgnl: Boolean = false) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
        if (hasOgnl && nonOptionParts.size > 2) info["OGNL"] = cleanQuotes(nonOptionParts[2])
    }

    private fun parseClassOnlyCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
    }

    private fun parseOgnlCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) {
            info["表达式"] = cleanQuotes(nonOptionParts.joinToString(" "))
        }
    }

    private fun parseGetstaticCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["字段"] = nonOptionParts[1]
    }

    private fun parseThreadCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["线程ID"] = nonOptionParts[0]
    }

    private fun parseVmtoolCommand(parts: List<String>, info: MutableMap<String, String>) {
        for (i in parts.indices) {
            when {
                parts[i] == "--action" && i + 1 < parts.size -> info["操作"] = parts[i + 1]
                parts[i] == "--className" && i + 1 < parts.size -> info["类"] = parts[i + 1]
            }
        }
    }

    private fun cleanQuotes(str: String): String {
        return str.trim().removeSurrounding("'").removeSurrounding("\"")
    }
}
