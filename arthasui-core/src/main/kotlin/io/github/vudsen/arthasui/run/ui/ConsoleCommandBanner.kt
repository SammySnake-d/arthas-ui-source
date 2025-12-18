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
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
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
    }
    
    /**
     * 注册监听器到指定的 template
     */
    fun registerListener(template: ArthasBridgeTemplate) {
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
    
    private fun showWaiting() {
        infoPanel.removeAll()
        infoPanel.add(waitingLabel)
        infoPanel.revalidate()
        infoPanel.repaint()
    }
    
    /**
     * 创建终止按钮 - 红色圆角方形，类似 IDE 的停止按钮
     */
    private fun createStopButton(): JComponent {
        return StopButton { stopCurrentCommand() }
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
        val parts = command.trim().split(Regex("\\s+"))
        
        if (parts.isEmpty()) return info
        
        val commandName = parts[0].lowercase()
        info["命令"] = parts[0]
        
        when (commandName) {
            "watch" -> parseClassMethodCommand(parts, info, hasOgnl = true)
            "trace", "stack", "monitor", "tt" -> parseClassMethodCommand(parts, info)
            "jad", "sc", "dump" -> parseClassOnlyCommand(parts, info)
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

/**
 * 红色圆角方形停止按钮，类似 IDE 的停止按钮样式
 */
private class StopButton(private val onClick: () -> Unit) : JComponent() {
    
    // 按钮颜色 - 红色系
    private val normalColor = Color(0xE05555)      // 正常状态：红色
    private val hoverColor = Color(0xC94545)       // 悬停状态：深红色
    private val borderColor = Color(0xD04545)      // 边框颜色
    
    private var isHovered = false
    
    init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "终止当前命令"
        preferredSize = Dimension(JBUI.scale(20), JBUI.scale(20))
        
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onClick()
            }
            
            override fun mouseEntered(e: MouseEvent) {
                isHovered = true
                repaint()
            }
            
            override fun mouseExited(e: MouseEvent) {
                isHovered = false
                repaint()
            }
        })
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        val size = JBUI.scale(16)
        val x = (width - size) / 2
        val y = (height - size) / 2
        val cornerRadius = JBUI.scale(3).toDouble()
        
        // 绘制圆角矩形背景
        val rect = RoundRectangle2D.Double(
            x.toDouble(), y.toDouble(),
            size.toDouble(), size.toDouble(),
            cornerRadius, cornerRadius
        )
        
        // 填充背景
        g2.color = if (isHovered) hoverColor else normalColor
        g2.fill(rect)
        
        // 绘制边框
        g2.color = borderColor
        g2.stroke = BasicStroke(1f)
        g2.draw(rect)
        
        g2.dispose()
    }
}
