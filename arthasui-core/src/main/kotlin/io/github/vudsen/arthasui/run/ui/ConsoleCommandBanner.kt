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

/**
 * A banner panel for the console that displays information about the currently executing command.
 * This is displayed at the top of the console window in the Run tool window.
 * 
 * 控制台命令信息横幅面板，显示当前正在执行的命令信息。
 * 该横幅显示在运行栏控制台窗口的上方。
 * 
 * 注意：由于 IntelliJ IDEA 插件 API 限制，无法在控制台标签上添加双击事件来跳转到执行窗口。
 * 作为替代方案，提供了"跳转到编辑器"按钮来实现类似功能。
 * 
 * Note: Due to IntelliJ IDEA plugin API limitations, it is not possible to add a double-click
 * event on the console tab to navigate to the execution window. As an alternative, a
 * "Jump to Editor" button is provided to achieve similar functionality.
 */
class ConsoleCommandBanner(
    private val project: Project,
    private val jvm: JVM,
    private val tabId: String?,
    private val displayName: String
) : JPanel(BorderLayout()) {

    private val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(2)))
    private val noCommandLabel = JLabel("等待执行命令...", SwingConstants.CENTER)

    private var currentCommand: String? = null
    private var listenerRegistered = false

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8))
        )
        background = JBColor(0xF5F5F5, 0x3C3F41)

        add(infoPanel, BorderLayout.CENTER)
        
        // Add navigation button to jump to query editor
        // 添加导航按钮，用于跳转到查询编辑器
        // 注意：IDEA 限制，无法实现双击控制台标签跳转到执行窗口，使用按钮作为替代
        val navigateButton = createNavigateButton()
        add(navigateButton, BorderLayout.EAST)
        
        updateDisplay(null)
        
        // Try to register listener immediately, or defer until template is available
        tryRegisterListener()
    }
    
    /**
     * Creates a button to navigate to the query editor.
     * 
     * 由于 IntelliJ IDEA 插件 API 限制，无法在控制台标签页上添加双击事件监听器，
     * 因此使用此按钮作为替代方案来跳转到执行窗口（查询编辑器）。
     */
    private fun createNavigateButton(): JLabel {
        val button = JLabel("跳转到编辑器", AllIcons.Actions.EditSource, SwingConstants.LEFT)
        button.toolTipText = "点击跳转到查询编辑器 (IDEA限制：无法通过双击标签跳转)"
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1),
            BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(6), JBUI.scale(2), JBUI.scale(6))
        )
        button.background = JBColor(0xE8E8E8, 0x3C3C3C)
        button.isOpaque = true
        
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                navigateToQueryEditor()
            }
            
            override fun mouseEntered(e: MouseEvent) {
                button.background = JBColor(0xD0D0D0, 0x4A4A4A)
            }
            
            override fun mouseExited(e: MouseEvent) {
                button.background = JBColor(0xE8E8E8, 0x3C3C3C)
            }
        })
        
        return button
    }
    
    /**
     * Navigate to the query editor for this JVM/tab.
     */
    private fun navigateToQueryEditor() {
        val fileEditorManager = FileEditorManager.getInstance(project)
        
        // Find the query console file by name
        fileEditorManager.allEditors.find { editor -> 
            editor.file.fileType == ArthasFileType && editor.file.name == displayName
        }?.let { editor ->
            fileEditorManager.openFile(editor.file, true, true)
        }
    }
    
    private fun tryRegisterListener() {
        if (listenerRegistered) return
        
        val executionManager = service<ArthasExecutionManager>()
        val template = executionManager.getTemplate(jvm, tabId)
        
        if (template != null) {
            listenerRegistered = true
            template.addListener(object : ArthasBridgeListener() {
                override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                    ApplicationManager.getApplication().invokeLater {
                        updateDisplay(command)
                    }
                }

                override fun onError(command: String, rawContent: String, exception: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        updateDisplay(command, isError = true)
                    }
                }
            })
        }
    }

    /**
     * Update the banner to show the current executing command.
     * Also attempts to register the listener if not done yet.
     */
    fun setExecutingCommand(command: String) {
        tryRegisterListener()
        currentCommand = command
        ApplicationManager.getApplication().invokeLater {
            updateDisplay(command, isExecuting = true)
        }
    }

    private fun updateDisplay(command: String?, isExecuting: Boolean = false, isError: Boolean = false) {
        infoPanel.removeAll()

        if (command.isNullOrBlank()) {
            infoPanel.add(noCommandLabel)
            infoPanel.revalidate()
            infoPanel.repaint()
            return
        }

        val commandInfo = parseCommandString(command)
        
        // Add status indicator
        val statusText = when {
            isExecuting -> "执行中"
            isError -> "错误"
            else -> "已完成"
        }
        val statusLabel = createInfoLabel("状态", statusText)
        if (isError) {
            statusLabel.foreground = JBColor.RED
        } else if (isExecuting) {
            statusLabel.foreground = JBColor.BLUE
        }
        infoPanel.add(statusLabel)
        
        for ((key, value) in commandInfo) {
            if (value.isNotBlank()) {
                val label = createInfoLabel(key, value)
                infoPanel.add(label)
            }
        }

        infoPanel.revalidate()
        infoPanel.repaint()
    }

    private fun createInfoLabel(key: String, value: String): JLabel {
        val label = JLabel("$key: $value")
        label.foreground = JBColor(0x333333, 0xBBBBBB)
        label.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1),
            BorderFactory.createEmptyBorder(JBUI.scale(2), JBUI.scale(6), JBUI.scale(2), JBUI.scale(6))
        )
        label.background = JBColor(0xFFFFFF, 0x2B2B2B)
        label.isOpaque = true
        return label
    }

    /**
     * Parse a raw command string to extract command info.
     * This is a simplified parser that works with raw command strings instead of PSI elements.
     */
    private fun parseCommandString(command: String): Map<String, String> {
        val info = linkedMapOf<String, String>()
        val parts = command.trim().split(Regex("\\s+"))
        
        if (parts.isEmpty()) return info
        
        val commandName = parts[0]
        info["命令"] = commandName
        
        // Parse based on command type
        when (commandName.lowercase()) {
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
        // watch class method [ognl] [options]
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        if (nonOptionParts.isNotEmpty()) info["类"] = nonOptionParts[0]
        if (nonOptionParts.size > 1) info["方法"] = nonOptionParts[1]
        if (nonOptionParts.size > 2) info["OGNL"] = cleanOgnl(nonOptionParts[2])
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
        if (nonOptionParts.isNotEmpty()) info["表达式"] = cleanOgnl(nonOptionParts.joinToString(" "))
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

    private fun cleanOgnl(ognl: String): String {
        return ognl.trim().removeSurrounding("'").removeSurrounding("\"")
    }
}
