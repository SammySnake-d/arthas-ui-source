package io.github.vudsen.arthasui.run.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.vudsen.arthasui.api.ArthasBridgeListener
import io.github.vudsen.arthasui.api.ArthasExecutionManager
import io.github.vudsen.arthasui.api.ArthasResultItem
import io.github.vudsen.arthasui.api.JVM
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
 * 停止按钮颜色表示状态：红色=运行中，灰色=已停止。
 */
class ConsoleCommandBanner(
    private val project: Project,
    private val jvm: JVM,
    private val tabId: String?,
    private val editorFileName: String
) : JPanel(BorderLayout()) {
    
    private val executionManager: ArthasExecutionManager
        get() = project.service()

    private val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)))
    private val waitingLabel = JLabel("等待执行命令...", SwingConstants.CENTER)
    
    private var listenerRegistered = false
    private lateinit var stopButton: JLabel
    private var isRunning = false
    private var busyCheckTimer: Timer? = null

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8))
        )
        background = JBColor(0xF7F7F7, 0x3C3F41)

        add(infoPanel, BorderLayout.CENTER)
        
        stopButton = createStopButton()
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(stopButton)
            add(createNavigateButton())
        }
        add(buttonPanel, BorderLayout.EAST)
        
        updateStopButtonState(false)
        showWaiting()
        
        SwingUtilities.invokeLater { tryRegisterListener() }
    }
    
    private fun tryRegisterListener() {
        if (listenerRegistered) return
        
        val template = executionManager.getTemplate(jvm, tabId) ?: return
        listenerRegistered = true
        
        var currentDisplayedCommand: String? = null
        
        template.addListener(object : ArthasBridgeListener() {
            override fun onContent(result: String) {
                // 当收到内容且 template 正在忙碌时，启动状态检查
                if (template.isBusy() && !isRunning) {
                    val cmd = executionManager.getCurrentCommand(jvm, tabId)
                    if (cmd != null) {
                        currentDisplayedCommand = cmd
                        ApplicationManager.getApplication().invokeLater {
                            updateStopButtonState(true)
                            updateDisplay(cmd)
                            startBusyCheck(template, currentDisplayedCommand)
                        }
                    }
                }
            }
            
            override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                currentDisplayedCommand = command
                ApplicationManager.getApplication().invokeLater {
                    stopBusyCheck()
                    updateStopButtonState(false)
                    updateDisplay(command)
                }
            }

            override fun onError(command: String, rawContent: String, exception: Exception) {
                currentDisplayedCommand = command
                ApplicationManager.getApplication().invokeLater {
                    stopBusyCheck()
                    updateStopButtonState(false)
                    updateDisplay(command)
                }
            }
        })
    }
    
    /**
     * 启动定时器检查 template 是否还在忙碌
     * 用于检测命令被取消的情况（取消时不会触发 onFinish/onError）
     * 
     * 检测逻辑：
     * 1. template.isBusy() 变为 false - 命令执行完毕或被取消
     * 2. getCurrentCommand() 返回 null - 任务已清理完毕
     */
    private fun startBusyCheck(template: io.github.vudsen.arthasui.api.ArthasBridgeTemplate, displayedCommand: String?) {
        stopBusyCheck()
        busyCheckTimer = Timer(100) {
            // 检查两个条件：template 不再忙碌，或者当前命令已被清除
            val templateNotBusy = !template.isBusy()
            val commandCleared = executionManager.getCurrentCommand(jvm, tabId) == null
            
            if ((templateNotBusy || commandCleared) && isRunning) {
                ApplicationManager.getApplication().invokeLater {
                    stopBusyCheck()
                    updateStopButtonState(false)
                    // 保持显示最后执行的命令
                    if (displayedCommand != null) {
                        updateDisplay(displayedCommand)
                    }
                }
            }
        }.apply {
            isRepeats = true
            start()
        }
    }
    
    /**
     * 停止忙碌状态检查定时器
     */
    private fun stopBusyCheck() {
        busyCheckTimer?.stop()
        busyCheckTimer = null
    }
    
    private fun showWaiting() {
        infoPanel.removeAll()
        infoPanel.add(waitingLabel)
        infoPanel.revalidate()
        infoPanel.repaint()
    }
    
    private fun createStopButton(): JLabel {
        return JLabel(AllIcons.Actions.Suspend).apply {
            toolTipText = "终止当前命令"
            border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(4), JBUI.scale(2), JBUI.scale(4))
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (isRunning) {
                        stopCurrentCommand()
                    }
                }
            })
        }
    }
    
    /**
     * 更新停止按钮状态
     * @param running true=运行中(红色可点击), false=已停止(灰色不可点击)
     */
    private fun updateStopButtonState(running: Boolean) {
        isRunning = running
        if (running) {
            stopButton.icon = AllIcons.Actions.Suspend
            stopButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            stopButton.toolTipText = "终止当前命令"
        } else {
            stopButton.icon = AllIcons.Process.Stop
            stopButton.cursor = Cursor.getDefaultCursor()
            stopButton.toolTipText = "没有正在运行的命令"
        }
    }
    
    private fun stopCurrentCommand() {
        executionManager.cancelCurrentCommand(jvm, tabId)
    }
    
    private fun createNavigateButton(): JLabel {
        return JLabel(editorFileName, AllIcons.General.Locate, SwingConstants.LEFT).apply {
            toolTipText = "跳转到查询编辑器: $editorFileName"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(4), JBUI.scale(2), JBUI.scale(4))
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    navigateToQueryEditor()
                }
            })
        }
    }
    
    private fun navigateToQueryEditor() {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.allEditors.find { editor -> 
            editor.file.fileType == ArthasFileType && editor.file.name == editorFileName
        }?.let { matchedEditor ->
            fileEditorManager.openFile(matchedEditor.file, true, true)
        }
    }

    /**
     * 更新横幅显示内容 - 不显示状态，状态由按钮颜色表示
     */
    private fun updateDisplay(command: String?) {
        infoPanel.removeAll()

        if (command.isNullOrBlank()) {
            showWaiting()
            return
        }

        val commandInfo = parseCommand(command)
        
        // 只显示命令参数，不显示状态标签
        commandInfo.forEach { (key, value) ->
            if (value.isNotBlank()) {
                infoPanel.add(createTag(key, value))
            }
        }

        infoPanel.revalidate()
        infoPanel.repaint()
    }

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

    private fun parseCommand(command: String): Map<String, String> {
        val info = linkedMapOf<String, String>()
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
