package io.github.vudsen.arthasui.core.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.vudsen.arthasui.api.conf.ArthasHistoryPersistent
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * 历史记录管理对话框
 * 
 * 显示所有已记录的主类及其历史数据统计，提供清除单个主类和清除全部的功能。
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */
class HistoryManagementDialog(
    private val project: Project
) : DialogWrapper(project) {

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        // Column indices
        private const val COL_MAIN_CLASS = 0
        private const val COL_COMMAND_COUNT = 1
        private const val COL_TT_RECORD_COUNT = 2
        private const val COL_LAST_CONNECT_TIME = 3
        
        private val COLUMN_NAMES = arrayOf("主类名", "命令数", "TT记录数", "最后连接时间")
    }

    private val persistent: ArthasHistoryPersistent = service()
    private val tableModel = HistoryTableModel()
    private val table = JBTable(tableModel)
    
    // 缓存的历史数据
    private val historyData = mutableListOf<HistoryRowData>()

    init {
        title = "历史记录管理"
        init()
        refreshTable()
    }


    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(700, 400)
        
        // Configure table
        table.setShowGrid(true)
        table.rowHeight = 28
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // Set column widths
        table.columnModel.getColumn(COL_MAIN_CLASS).preferredWidth = 350
        table.columnModel.getColumn(COL_COMMAND_COUNT).preferredWidth = 80
        table.columnModel.getColumn(COL_TT_RECORD_COUNT).preferredWidth = 80
        table.columnModel.getColumn(COL_LAST_CONNECT_TIME).preferredWidth = 150
        
        // Add table to scroll pane
        val scrollPane = JBScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // Create button panel
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        buttonPanel.border = JBUI.Borders.empty(5)
        
        val clearSelectedButton = JButton("清除选中")
        clearSelectedButton.addActionListener { clearSelected() }
        buttonPanel.add(clearSelectedButton)
        
        val clearAllButton = JButton("清除全部")
        clearAllButton.addActionListener { clearAll() }
        buttonPanel.add(clearAllButton)
        
        val refreshButton = JButton("刷新")
        refreshButton.addActionListener { refreshTable() }
        buttonPanel.add(refreshButton)
        
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }

    /**
     * 刷新表格数据
     * 
     * 从 ArthasHistoryPersistent 获取所有主类的历史数据并更新表格
     */
    fun refreshTable() {
        historyData.clear()
        
        val mainClasses = persistent.getAllMainClasses()
        for (mainClass in mainClasses) {
            val history = persistent.getHistory(mainClass)
            if (history != null) {
                historyData.add(HistoryRowData(
                    mainClass = mainClass,
                    commandCount = history.commandHistory.size,
                    ttRecordCount = history.ttRecords.size,
                    lastConnectTime = history.lastConnectTime
                ))
            }
        }
        
        // Sort by last connect time (most recent first)
        historyData.sortByDescending { it.lastConnectTime ?: 0L }
        
        tableModel.fireTableDataChanged()
    }

    /**
     * 清除选中的主类历史数据
     * 
     * Requirements: 5.2
     */
    fun clearSelected() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            Messages.showWarningDialog(
                project,
                "请先选择要清除的主类",
                "未选择"
            )
            return
        }
        
        val rowData = historyData.getOrNull(selectedRow) ?: return
        val mainClass = rowData.mainClass
        
        val result = Messages.showYesNoDialog(
            project,
            "确定要清除 \"$mainClass\" 的所有历史数据吗？\n\n" +
            "命令记录: ${rowData.commandCount} 条\n" +
            "TT记录: ${rowData.ttRecordCount} 条",
            "确认清除",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            persistent.clearHistory(mainClass)
            refreshTable()
        }
    }

    /**
     * 清除所有历史数据
     * 
     * Requirements: 5.3
     */
    fun clearAll() {
        if (historyData.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "没有历史数据需要清除",
                "提示"
            )
            return
        }
        
        val totalCommands = historyData.sumOf { it.commandCount }
        val totalTtRecords = historyData.sumOf { it.ttRecordCount }
        
        val result = Messages.showYesNoDialog(
            project,
            "确定要清除所有历史数据吗？\n\n" +
            "主类数量: ${historyData.size}\n" +
            "命令记录总数: $totalCommands 条\n" +
            "TT记录总数: $totalTtRecords 条\n\n" +
            "此操作不可撤销！",
            "确认清除全部",
            Messages.getWarningIcon()
        )
        
        if (result == Messages.YES) {
            persistent.clearAllHistory()
            refreshTable()
        }
    }

    /**
     * 历史数据行数据类
     */
    private data class HistoryRowData(
        val mainClass: String,
        val commandCount: Int,
        val ttRecordCount: Int,
        val lastConnectTime: Long?
    )

    /**
     * 历史数据表格模型
     */
    private inner class HistoryTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = historyData.size

        override fun getColumnCount(): Int = COLUMN_NAMES.size

        override fun getColumnName(column: Int): String = COLUMN_NAMES[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val rowData = historyData.getOrNull(rowIndex) ?: return null
            return when (columnIndex) {
                COL_MAIN_CLASS -> rowData.mainClass
                COL_COMMAND_COUNT -> rowData.commandCount
                COL_TT_RECORD_COUNT -> rowData.ttRecordCount
                COL_LAST_CONNECT_TIME -> {
                    rowData.lastConnectTime?.let { DATE_FORMAT.format(Date(it)) } ?: "-"
                }
                else -> null
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }
}
