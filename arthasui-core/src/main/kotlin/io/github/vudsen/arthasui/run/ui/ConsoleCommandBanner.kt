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
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * 控制台命令信息横幅面板。
 * 
 * 显示当前执行命令的解析信息（命令类型、类名、方法名、OGNL表达式等），
 * 以"描述: 值"的格式展示在控制台上方。
 * 
 * 功能：
 * 1. 解析并显示 Arthas 命令的关键参数
 * 2. 提供"跳转到编辑器"按钮，快速定位到执行命令的窗口
 * 3. 实时更新命令执行状态
 */
class ConsoleCommandBanner(
    private val project: Project,
    private val jvm: JVM,
    private val tabId: String?,
    private val displayName: String
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
        add(createNavigateButton(), BorderLayout.EAST)
        
        showWaiting()
        
        // 延迟注册监听器，避免在初始化时阻塞
        SwingUtilities.invokeLater { tryRegisterListener() }
    }
    
    private fun showWaiting() {
        infoPanel.removeAll()
        infoPanel.add(waitingLabel)
        infoPanel.revalidate()
        infoPanel.repaint()
    }
    
    /**
     * 创建跳转按钮，点击后跳转到对应的查询编辑器窗口
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
            editor.file.fileType == ArthasFileType && editor.file.name == displayName
        }?.let { matchedEditor ->
            fileEditorManager.openFile(matchedEditor.file, true, true)
        }
    }
    
    /**
     * 尝试注册命令执行监听器
     */
    private fun tryRegisterListener() {
        if (listenerRegistered) return
        
        val template = service<ArthasExecutionManager>().getTemplate(jvm, tabId) ?: return
        
        listenerRegistered = true
        template.addListener(object : ArthasBridgeListener() {
            override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateDisplay(command, isError = false)
                }
            }

            override fun onError(command: String, rawContent: String, exception: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    updateDisplay(command, isError = true)
                }
            }
        })
    }

    /**
     * 更新横幅显示内容
     */
    private fun updateDisplay(command: String?, isError: Boolean = false) {
        infoPanel.removeAll()

        if (command.isNullOrBlank()) {
            showWaiting()
            return
        }

        val commandInfo = parseCommand(command)
        
        // 添加状态标签
        val statusText = if (isError) "错误" else "已完成"
        val statusLabel = createTag("状态", statusText)
        if (isError) {
            statusLabel.foreground = JBColor.RED
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
     * 创建标签组件，格式为"描述: 值"
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
     * 解析 Arthas 命令，提取关键参数
     */
    private fun parseCommand(command: String): Map<String, String> {
        val info = linkedMapOf<String, String>()
        val parts = command.trim().split(Regex("\\s+"))
        
        if (parts.isEmpty()) return info
        
        val commandName = parts[0].lowercase()
        info["命令"] = parts[0]
        
        when (commandName) {
            "watch" -> parseWatchCommand(parts, info)
            "trace" -> parseTraceCommand(parts, info)
            "stack" -> parseStackCommand(parts, info)
            "jad" -> parseJadCommand(parts, info)
            "sc" -> parseScCommand(parts, info)
            "sm" -> parseSmCommand(parts, info)
            "monitor" -> parseMonitorCommand(parts, info)
            "tt" -> parseTtCommand(parts, info)
            "ognl" -> parseOgnlCommand(parts, info)
            "getstatic" -> parseGetstaticCommand(parts, info)
            "dump" -> parseDumpCommand(parts, info)
            "thread" -> parseThreadCommand(parts, info)
            "vmtool" -> parseVmtoolCommand(parts, info)
        }
        
        return info
    }

    private fun parseWatchCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
        if (nonOptionParts.size > 2) info["OGNL"] = cleanQuotes(nonOptionParts[2])
    }

    private fun parseTraceCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
    }

    private fun parseStackCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
    }

    private fun parseJadCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
    }

    private fun parseScCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
    }

    private fun parseSmCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
    }

    private fun parseMonitorCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
    }

    private fun parseTtCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
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

    private fun parseDumpCommand(parts: List<String>, info: MutableMap<String, String>) {
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
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

    /**
     * 移除字符串两端的引号
     */
    private fun cleanQuotes(str: String): String {
        return str.trim().removeSurrounding("'").removeSurrounding("\"")
    }
}
