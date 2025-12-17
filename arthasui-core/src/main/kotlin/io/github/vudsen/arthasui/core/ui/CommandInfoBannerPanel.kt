package io.github.vudsen.arthasui.core.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import io.github.vudsen.arthasui.language.arthas.psi.*
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * A read-only banner panel that displays parsed command information.
 * Shows command type, class name, method name, and other relevant parameters
 * in a "Description: Value" format.
 */
class CommandInfoBannerPanel(
    private val project: Project,
    private val editor: Editor
) : JPanel(BorderLayout()) {

    private val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(2)))
    private val noSelectionLabel = JLabel("Select a command to see details", SwingConstants.CENTER)

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8))
        )
        background = JBColor(0xF5F5F5, 0x3C3F41)

        add(infoPanel, BorderLayout.CENTER)
        updateDisplay(null)

        // Add listeners for selection and caret changes
        editor.selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                updateFromSelection()
            }
        })

        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                updateFromCaret()
            }
        })
    }

    private fun updateFromSelection() {
        ApplicationManager.getApplication().runReadAction {
            val selectedText = editor.selectionModel.selectedText
            if (selectedText.isNullOrBlank()) {
                updateFromCaret()
                return@runReadAction
            }
            
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (psiFile == null) {
                updateDisplay(null)
                return@runReadAction
            }

            val startOffset = editor.selectionModel.selectionStart
            val element = psiFile.findElementAt(startOffset)
            val command = PsiTreeUtil.getParentOfType(element, ArthasCommand::class.java)
            
            ApplicationManager.getApplication().invokeLater {
                updateDisplay(command)
            }
        }
    }

    private fun updateFromCaret() {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            if (psiFile == null) {
                updateDisplay(null)
                return@runReadAction
            }

            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)
            val command = PsiTreeUtil.getParentOfType(element, ArthasCommand::class.java)
            
            ApplicationManager.getApplication().invokeLater {
                updateDisplay(command)
            }
        }
    }

    private fun updateDisplay(command: ArthasCommand?) {
        infoPanel.removeAll()

        if (command == null) {
            infoPanel.add(noSelectionLabel)
            infoPanel.revalidate()
            infoPanel.repaint()
            return
        }

        val commandInfo = parseCommand(command)
        
        for ((key, value) in commandInfo) {
            if (value.isNotBlank()) {
                val label = createInfoLabel(key, value)
                infoPanel.add(label)
            }
        }

        if (infoPanel.componentCount == 0) {
            infoPanel.add(noSelectionLabel)
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

    private fun parseCommand(command: ArthasCommand): Map<String, String> {
        val info = linkedMapOf<String, String>()
        
        // Get command name from the first child element (the command keyword)
        val commandName = getCommandName(command)
        info["Command"] = commandName

        // Parse specific command types
        when (command) {
            is ArthasWatchCommand -> {
                command.argumentList.find { it.text.startsWith("-c") }?.let {
                    info["Class Loader"] = extractArgumentValue(it)
                }
                findClassPattern(command)?.let { info["Class"] = it }
                command.identifier?.text?.let { info["Method"] = it }
                command.ognl?.text?.let { info["OGNL"] = cleanOgnlExpression(it) }
            }
            is ArthasTraceCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
                command.method?.text?.let { info["Method"] = it }
                command.ognl?.text?.let { info["OGNL"] = cleanOgnlExpression(it) }
            }
            is ArthasStackCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
                command.identifier?.text?.let { info["Method"] = it }
                command.ognl?.text?.let { info["OGNL"] = cleanOgnlExpression(it) }
            }
            is ArthasJadCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
            }
            is ArthasScCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
                command.identifier?.text?.let { info["Method"] = it }
            }
            is ArthasSmCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
                command.method?.text?.let { info["Method"] = it }
            }
            is ArthasMonitorCommand -> {
                command.classPattern?.text?.let { info["Class"] = it }
                command.identifier?.text?.let { info["Method"] = it }
            }
            is ArthasTtCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
                command.method?.text?.let { info["Method"] = it }
            }
            is ArthasOgnlCommand -> {
                command.ognl.text?.let { info["Expression"] = cleanOgnlExpression(it) }
            }
            is ArthasGetstaticCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
                command.method?.text?.let { info["Field"] = it }
                command.ognl?.text?.let { info["OGNL"] = cleanOgnlExpression(it) }
            }
            is ArthasDumpCommand -> {
                command.clazz?.text?.let { info["Class"] = it }
            }
            is ArthasThreadCommand -> {
                // Thread command might have thread id
                findIdentifier(command)?.let { info["Thread ID"] = it }
            }
            is ArthasVmtoolCommand -> {
                command.argumentList.forEach { arg ->
                    when {
                        arg.text.startsWith("-c") -> info["Class Loader"] = extractArgumentValue(arg)
                        arg.text.startsWith("--className") -> info["Class"] = extractArgumentValue(arg)
                        arg.text.startsWith("--action") -> info["Action"] = extractArgumentValue(arg)
                    }
                }
            }
        }

        // Add common arguments info
        parseCommonArguments(command, info)

        return info
    }

    private fun getCommandName(command: ArthasCommand): String {
        return command.firstChild?.text ?: "Unknown"
    }

    private fun findClassPattern(command: PsiElement): String? {
        for (child in command.children) {
            if (child is ArthasClazz) {
                return child.text
            }
        }
        // Fallback: look for CLASS_PATTERN token type
        return command.children.firstOrNull { 
            it.node?.elementType?.toString() == "CLASS_PATTERN" 
        }?.text
    }

    private fun findIdentifier(command: PsiElement): String? {
        return command.children.firstOrNull { 
            it.node?.elementType?.toString() == "IDENTIFIER" 
        }?.text
    }

    private fun extractArgumentValue(argument: ArthasArgument): String {
        // Arguments are in format "-x value" or "--xxx value"
        val parts = argument.text.split(Regex("\\s+"), 2)
        return if (parts.size > 1) parts[1] else ""
    }

    private fun cleanOgnlExpression(ognl: String): String {
        // Remove surrounding quotes if present
        return ognl.trim().removeSurrounding("'").removeSurrounding("\"")
    }

    private fun parseCommonArguments(command: ArthasCommand, info: MutableMap<String, String>) {
        val arguments = when (command) {
            is ArthasWatchCommand -> command.argumentList
            is ArthasTraceCommand -> command.argumentList
            is ArthasStackCommand -> command.argumentList
            is ArthasMonitorCommand -> command.argumentList
            is ArthasTtCommand -> command.argumentList
            is ArthasVmtoolCommand -> command.argumentList
            else -> return
        }

        for (arg in arguments) {
            val argText = arg.text
            when {
                argText.startsWith("-n") -> info["Limit"] = extractArgumentValue(arg)
                argText.startsWith("-x") -> info["Expand Level"] = extractArgumentValue(arg)
                argText.startsWith("-E") -> info["Regex"] = "enabled"
                argText.startsWith("-b") -> info["Before"] = "enabled"
                argText.startsWith("-e") -> info["Exception"] = "enabled"
                argText.startsWith("-s") -> info["Success"] = "enabled"
                argText.startsWith("-f") -> info["Finish"] = "enabled"
            }
        }
    }
}
