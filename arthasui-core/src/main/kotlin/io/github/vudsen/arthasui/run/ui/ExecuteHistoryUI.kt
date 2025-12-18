package io.github.vudsen.arthasui.run.ui

import com.intellij.diagnostic.logging.AdditionalTabComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.vudsen.arthasui.api.ArthasBridgeListener
import io.github.vudsen.arthasui.api.ArthasExecutionManager
import io.github.vudsen.arthasui.api.ArthasResultItem
import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.api.util.collectStackTrace
import io.github.vudsen.arthasui.core.ArthasHistoryService
import io.github.vudsen.arthasui.util.ui.CardJPanel
import io.github.vudsen.arthasui.util.ui.TagLabel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.util.LinkedList
import javax.swing.*

class ExecuteHistoryUI(project: Project, jvm: JVM, tabId: String? = null) : AdditionalTabComponent() {

    private val historyCommand = CollectionListModel<CardItem>(LinkedList(), true)

    private var contentRight = CardJPanel()

    companion object {
        private enum class Tag(val color: Color) {
            // FIXME: color contains compatibility problem.
            SUCCESS(EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES).let { it.backgroundColor ?: it.foregroundColor }),
            ERROR(EditorColorsManager.getInstance().globalScheme.getAttributes(HighlighterColors.BAD_CHARACTER).let { it.backgroundColor ?: it.foregroundColor }),
            // Historical records from previous sessions - displayed with gray color
            HISTORY_SUCCESS(Color(128, 128, 128)),
            HISTORY_ERROR(Color(160, 100, 100));
        }
        private data class CardItem (
            var command: String,
            var id: String,
            var tag: Tag,
            var isHistorical: Boolean = false  // Flag to indicate if this is from a previous session
        )
        val EXCEPTION_CHECK_PATTER = Regex("^(\\w+\\.)+\\w+Exception")
    }

    init {
        layout = BorderLayout()
        val splitter = JBSplitter(false, 0.2f, 0.05f, 0.95f).apply {
            firstComponent = createHistoryLeft()
            secondComponent = contentRight
        }
        add(splitter, BorderLayout.CENTER)
        splitter.divider.border = JBUI.Borders.customLineRight(JBUI.CurrentTheme.Toolbar.SEPARATOR_COLOR)
        val coordinator = project.getService(ArthasExecutionManager::class.java)
        val template = coordinator.getTemplate(jvm, tabId)!!

        // Load historical command records from previous sessions
        // Requirements: 4.2, 4.5 - Load historical data when connecting to a JVM with previously seen main class
        loadHistoricalRecords(project, jvm)

        template.addListener(object : ArthasBridgeListener() {

            override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                val item = CardItem(
                    command,
                    System.currentTimeMillis().toString(),
                    if (isCommandExecuteFailed(rawContent)) Tag.ERROR else Tag.SUCCESS,
                    isHistorical = false
                )
                historyCommand.add(0, item)

                ApplicationManager.getApplication().invokeLater {
                    contentRight.add(HistoryDetailUI(project, rawContent, result), item.id)
                }
            }

            override fun onError(command: String, rawContent: String, exception: Exception) {
                val item = CardItem(command, System.currentTimeMillis().toString(), Tag.ERROR, isHistorical = false)
                historyCommand.add(0, item)

                ApplicationManager.getApplication().invokeLater {
                    contentRight.add(HistoryDetailUI(project, rawContent +
                            "\n\nPlugin Stack Trace(Our plugin is currently unstable, and we cannot determine whether the error originates from our plugin or your command. Therefore, we are providing the full stack trace below.):\n"
                            + exception.collectStackTrace(), null), item.id)
                }
            }
        })
    }

    /**
     * Load historical command records from ArthasHistoryService
     * Historical records are displayed with gray background to indicate they are from previous sessions
     * and replay is unavailable for these records.
     * 
     * Requirements: 4.2, 4.5
     */
    private fun loadHistoricalRecords(project: Project, jvm: JVM) {
        val historyService = project.getService(ArthasHistoryService::class.java)
        val mainClass = jvm.name
        val historicalCommands = historyService.getCommandHistory(mainClass)
        
        // Add historical records to the list (oldest first, so newest appears at top after reversal)
        // Historical records are marked with HISTORY_SUCCESS or HISTORY_ERROR tags
        for (record in historicalCommands.reversed()) {
            val tag = if (record.success) Tag.HISTORY_SUCCESS else Tag.HISTORY_ERROR
            val item = CardItem(
                command = record.command,
                id = "history_${record.timestamp}",
                tag = tag,
                isHistorical = true
            )
            historyCommand.add(item)
            
            // Add a placeholder detail panel for historical records
            // Note: We don't have the raw output for historical records, so we show a message
            ApplicationManager.getApplication().invokeLater {
                contentRight.add(
                    HistoryDetailUI(
                        project,
                        "[Historical Record]\nCommand: ${record.command}\nTimestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(record.timestamp))}\nStatus: ${if (record.success) "Success" else "Error"}\n\nNote: Detailed output is not available for historical records from previous sessions.",
                        null
                    ),
                    item.id
                )
            }
        }
    }

    private fun isCommandExecuteFailed(frame: String): Boolean {
        return EXCEPTION_CHECK_PATTER.containsMatchIn(frame)
    }

    override fun dispose() {

    }

    override fun getPreferredFocusableComponent(): JComponent {
        return this
    }

    override fun getToolbarActions(): ActionGroup? {
        return null
    }

    override fun getSearchComponent(): JComponent? {
        return null
    }

    override fun getToolbarPlace(): String {
        return ActionPlaces.TOOLBAR
    }

    override fun getToolbarContextComponent(): JComponent? {
        return null
    }

    override fun isContentBuiltIn(): Boolean {
        return false
    }

    override fun getTabTitle(): String {
        return "History"
    }

    private fun createHistoryLeft(): JComponent {
        val list = JBList(historyCommand).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            setEmptyText("No command executed yet")
            installCellRenderer { cell ->
                JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    // Display tag name without HISTORY_ prefix for cleaner UI
                    val tagDisplayName = when (cell.tag) {
                        Tag.HISTORY_SUCCESS -> "SUCCESS"
                        Tag.HISTORY_ERROR -> "ERROR"
                        else -> cell.tag.name
                    }
                    val label = TagLabel(tagDisplayName, cell.tag.color)
                    add(label)
                    
                    // Add command label with italic style for historical records
                    val commandLabel = JBLabel(cell.command)
                    if (cell.isHistorical) {
                        // Historical records: italic font and gray foreground to indicate they are from previous sessions
                        commandLabel.font = commandLabel.font.deriveFont(java.awt.Font.ITALIC)
                        commandLabel.foreground = Color.GRAY
                    }
                    add(commandLabel)
                    
                    // Add a small indicator for historical records
                    if (cell.isHistorical) {
                        val historyIndicator = JBLabel(" [历史]")
                        historyIndicator.foreground = Color.GRAY
                        historyIndicator.font = historyIndicator.font.deriveFont(java.awt.Font.ITALIC, 10f)
                        add(historyIndicator)
                    }
                }
            }
        }
        list.addListSelectionListener { evt ->
            val jbList = evt.source as JBList<*>
            if (jbList.selectedIndex >= 0) {
                contentRight.layout.show(contentRight, historyCommand.getElementAt(jbList.selectedIndex).id)
            }
        }
        val pane = JBScrollPane(list)
        return pane
    }




}