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
    private var currentDisplayedCommand: String? = null
    
    /** 命令完成回调，用于检测取消等不触发 onFinish/onError 的情况 */
    private val commandFinishListener: (String?) -> Unit = { lastCommand ->
        ApplicationManager.getApplication().invokeLater {
            if (isRunning) {
                updateStopButtonState(false)
                // 保持显示最后执行的命令
                updateDisplay(lastCommand ?: currentDisplayedCommand)
            }
        }
    }

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
        
        // 注册命令完成监听器
        executionManager.addCommandFinishListener(jvm, tabId, commandFinishListener)
        
        SwingUtilities.invokeLater { tryRegisterListener() }
    }
    
    private fun tryRegisterListener() {
        if (listenerRegistered) return
        
        val template = executionManager.getTemplate(jvm, tabId) ?: return
        listenerRegistered = true
        
        template.addListener(object : ArthasBridgeListener() {
            override fun onContent(result: String) {
                // 当收到内容且 template 正在忙碌时，更新状态
                if (template.isBusy() && !isRunning) {
                    val cmd = executionManager.getCurrentCommand(jvm, tabId)
                    if (cmd != null) {
                        currentDisplayedCommand = cmd
                        ApplicationManager.getApplication().invokeLater {
                            updateStopButtonState(true)
                            updateDisplay(cmd)
                        }
                    }
                }
            }
            
            override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                currentDisplayedCommand = command
                ApplicationManager.getApplication().invokeLater {
                    updateStopButtonState(false)
                    updateDisplay(command)
                }
            }

            override fun onError(command: String, rawContent: String, exception: Exception) {
                currentDisplayedCommand = command
                ApplicationManager.getApplication().invokeLater {
                    updateStopButtonState(false)
                    updateDisplay(command)
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
        
        // 使用智能分割，保留引号内的内容
        val tokens = tokenizeCommand(cleanCommand)
        
        if (tokens.isEmpty()) return info
        
        val commandName = tokens[0].lowercase()
        info["命令"] = tokens[0]
        
        when (commandName) {
            "watch" -> parseWatchCommand(cleanCommand, tokens, info)
            "trace", "stack", "monitor", "tt" -> parseClassMethodCommand(tokens, info)
            "jad", "sc", "dump", "classloader", "memory", "heapdump" -> parseClassOnlyCommand(tokens, info)
            "sm" -> parseClassMethodCommand(tokens, info)
            "ognl" -> parseOgnlCommand(cleanCommand, info)
            "getstatic" -> parseGetstaticCommand(tokens, info)
            "thread" -> parseThreadCommand(tokens, info)
            "vmtool" -> parseVmtoolCommand(tokens, info)
        }
        
        return info
    }
    
    /**
     * 智能分割命令，保留引号内的内容作为一个整体
     */
    private fun tokenizeCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        var braceDepth = 0
        
        for (ch in command) {
            when {
                !inQuote && (ch == '\'' || ch == '"') -> {
                    inQuote = true
                    quoteChar = ch
                    current.append(ch)
                }
                inQuote && ch == quoteChar && braceDepth == 0 -> {
                    inQuote = false
                    current.append(ch)
                }
                ch == '{' -> {
                    braceDepth++
                    current.append(ch)
                }
                ch == '}' -> {
                    braceDepth = maxOf(0, braceDepth - 1)
                    current.append(ch)
                }
                !inQuote && braceDepth == 0 && ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }
    
    /**
     * 解析 watch 命令，特殊处理 OGNL 表达式
     * watch class method [ognl] [options]
     */
    private fun parseWatchCommand(command: String, tokens: List<String>, info: MutableMap<String, String>) {
        val nonOptionTokens = tokens.drop(1).filter { !it.startsWith("-") }
        if (nonOptionTokens.isNotEmpty()) info["类"] = nonOptionTokens[0]
        if (nonOptionTokens.size > 1) info["方法"] = nonOptionTokens[1]
        if (nonOptionTokens.size > 2) {
            // OGNL 表达式可能包含引号
            info["OGNL"] = cleanQuotes(nonOptionTokens[2])
        }
    }

    private fun parseClassMethodCommand(tokens: List<String>, info: MutableMap<String, String>) {
        val nonOptionTokens = tokens.drop(1).filter { !it.startsWith("-") }
        if (nonOptionTokens.isNotEmpty()) info["类"] = nonOptionTokens[0]
        if (nonOptionTokens.size > 1) info["方法"] = nonOptionTokens[1]
    }

    private fun parseClassOnlyCommand(tokens: List<String>, info: MutableMap<String, String>) {
        val nonOptionTokens = tokens.drop(1).filter { !it.startsWith("-") }
        if (nonOptionTokens.isNotEmpty()) info["类"] = nonOptionTokens[0]
    }

    private fun parseOgnlCommand(command: String, info: MutableMap<String, String>) {
        // ognl 命令的表达式可能很复杂，提取引号内的内容
        val exprMatch = Regex("ognl\\s+(?:-[^\\s]+\\s+)*['\"](.+?)['\"]", RegexOption.DOT_MATCHES_ALL).find(command)
            ?: Regex("ognl\\s+(?:-[^\\s]+\\s+)*([^\\s-]+)").find(command)
        exprMatch?.let {
            info["表达式"] = it.groupValues[1]
        }
    }

    private fun parseGetstaticCommand(tokens: List<String>, info: MutableMap<String, String>) {
        val nonOptionTokens = tokens.drop(1).filter { !it.startsWith("-") }
        if (nonOptionTokens.isNotEmpty()) info["类"] = nonOptionTokens[0]
        if (nonOptionTokens.size > 1) info["字段"] = nonOptionTokens[1]
    }

    private fun parseThreadCommand(tokens: List<String>, info: MutableMap<String, String>) {
        val nonOptionTokens = tokens.drop(1).filter { !it.startsWith("-") }
        if (nonOptionTokens.isNotEmpty()) info["线程ID"] = nonOptionTokens[0]
    }

    private fun parseVmtoolCommand(tokens: List<String>, info: MutableMap<String, String>) {
        for (i in tokens.indices) {
            when {
                tokens[i] == "--action" && i + 1 < tokens.size -> info["操作"] = tokens[i + 1]
                tokens[i] == "--className" && i + 1 < tokens.size -> info["类"] = tokens[i + 1]
            }
        }
    }

    private fun cleanQuotes(str: String): String {
        return str.trim().removeSurrounding("'").removeSurrounding("\"")
    }
}
