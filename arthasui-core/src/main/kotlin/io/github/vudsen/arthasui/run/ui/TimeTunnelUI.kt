package io.github.vudsen.arthasui.run.ui

import com.intellij.diagnostic.logging.AdditionalTabComponent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.vudsen.arthasui.api.ArthasBridgeListener
import io.github.vudsen.arthasui.api.ArthasExecutionManager
import io.github.vudsen.arthasui.api.ArthasResultItem
import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.api.bean.TtRecordItem
import io.github.vudsen.arthasui.common.parser.TtOutputParser
import io.github.vudsen.arthasui.common.parser.TtReplayResult
import io.github.vudsen.arthasui.core.ArthasHistoryService
import io.github.vudsen.arthasui.core.ui.HistoryManagementDialog
import io.github.vudsen.arthasui.core.ui.OgnlTreeDisplayUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * TimeTunnel 面板
 * 
 * 作为 AdditionalTabComponent 添加到 Run 控制台，提供 TimeTunnel 命令的图形化操作界面。
 * 
 * 功能：
 * - 记录方法调用（tt -t）
 * - 查看记录列表（tt -l）
 * - 查看记录详情（tt -i）
 * - 重放方法调用（tt -p）
 * 
 * Requirements: 1.1, 1.2
 */
class TimeTunnelUI(
    private val project: Project,
    private val jvm: JVM,
    private val tabId: String? = null
) : AdditionalTabComponent() {

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        // Column indices
        private const val COL_INDEX = 0
        private const val COL_TIMESTAMP = 1
        private const val COL_CLASS_METHOD = 2
        private const val COL_COST = 3
        private const val COL_STATUS = 4
        private const val COL_ACTION = 5
        
        private val COLUMN_NAMES = arrayOf("INDEX", "时间戳", "类.方法", "耗时(ms)", "状态", "操作")
    }

    // UI Components - Input Area
    private val classNameField = JBTextField(30)
    private val methodNameField = JBTextField(20)
    private val recordCountSpinner = JSpinner(SpinnerNumberModel(10, 1, 1000, 1))
    private val startButton = JButton("开始记录")
    private val stopButton = JButton("停止记录")
    private val refreshButton = JButton("刷新")
    private val historyManageButton = JButton()
    private val statusLabel = JBLabel("空闲")

    // UI Components - Record List
    private val recordTableModel = TtRecordTableModel()
    private val recordTable = JBTable(recordTableModel)

    // UI Components - Detail Panel
    private val detailTabbedPane = JBTabbedPane()
    private val rawDetailArea = JTextArea()
    private var parsedDetailPanel: JPanel = JPanel(BorderLayout())

    // State
    private var isRecording = false
    private var currentSessionId = ""
    private var capturedCount = 0
    private val records = mutableListOf<TtRecordItem>()

    // Services
    private val historyService: ArthasHistoryService = project.getService(ArthasHistoryService::class.java)
    private val executionManager: ArthasExecutionManager = project.getService(ArthasExecutionManager::class.java)

    init {
        layout = BorderLayout()
        
        // Create main layout: top input area + center list + bottom detail
        val mainSplitter = JBSplitter(true, 0.6f, 0.3f, 0.9f)
        mainSplitter.firstComponent = createTopPanel()
        mainSplitter.secondComponent = createDetailPanel()
        
        add(mainSplitter, BorderLayout.CENTER)
        
        // Initialize button states
        stopButton.isEnabled = false
        
        // Setup event listeners
        setupEventListeners()
        
        // Setup bridge listener for tt command sync
        setupBridgeListener()
    }

    /**
     * Creates the top panel containing input area and record list
     */
    private fun createTopPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createInputArea(), BorderLayout.NORTH)
        panel.add(createRecordListPanel(), BorderLayout.CENTER)
        return panel
    }

    /**
     * Creates the input area with class name, method name, record count, and buttons
     * Requirements: 1.2, 1.4
     */
    private fun createInputArea(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        panel.border = JBUI.Borders.empty(5)
        
        // Class name input
        panel.add(JBLabel("类名:"))
        classNameField.toolTipText = "输入要记录的类名，支持通配符 *"
        panel.add(classNameField)
        
        // Method name input
        panel.add(JBLabel("方法名:"))
        methodNameField.toolTipText = "输入要记录的方法名，支持通配符 *"
        panel.add(methodNameField)
        
        // Record count spinner
        panel.add(JBLabel("记录次数:"))
        recordCountSpinner.toolTipText = "最大记录次数"
        panel.add(recordCountSpinner)
        
        // Buttons
        startButton.icon = AllIcons.Actions.Execute
        panel.add(startButton)
        
        stopButton.icon = AllIcons.Actions.Suspend
        panel.add(stopButton)
        
        refreshButton.icon = AllIcons.Actions.Refresh
        panel.add(refreshButton)
        
        // History management button
        // Requirements: 5.1 - WHEN the user opens the history management interface THEN the ArthasUI SHALL display a list of all main classes with record counts
        historyManageButton.icon = AllIcons.General.GearPlain
        historyManageButton.toolTipText = "管理历史记录"
        panel.add(historyManageButton)
        
        // Status indicator
        panel.add(Box.createHorizontalStrut(10))
        panel.add(JBLabel("状态:"))
        statusLabel.border = JBUI.Borders.empty(0, 5)
        panel.add(statusLabel)
        
        return panel
    }

    /**
     * Creates the record list panel with JBTable
     * Requirements: 2.1, 2.2, 2.3
     */
    private fun createRecordListPanel(): JComponent {
        // Configure table
        recordTable.setShowGrid(true)
        recordTable.rowHeight = 28
        recordTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // Set column widths
        recordTable.columnModel.getColumn(COL_INDEX).preferredWidth = 60
        recordTable.columnModel.getColumn(COL_TIMESTAMP).preferredWidth = 150
        recordTable.columnModel.getColumn(COL_CLASS_METHOD).preferredWidth = 300
        recordTable.columnModel.getColumn(COL_COST).preferredWidth = 80
        recordTable.columnModel.getColumn(COL_STATUS).preferredWidth = 60
        recordTable.columnModel.getColumn(COL_ACTION).preferredWidth = 80
        
        // Set status column renderer with icons
        recordTable.columnModel.getColumn(COL_STATUS).cellRenderer = StatusCellRenderer()
        
        // Set action column renderer and editor
        recordTable.columnModel.getColumn(COL_ACTION).cellRenderer = ActionCellRenderer()
        recordTable.columnModel.getColumn(COL_ACTION).cellEditor = ActionCellEditor()
        
        // Add selection listener for showing details
        recordTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && recordTable.selectedRow >= 0) {
                val selectedRecord = records.getOrNull(recordTable.selectedRow)
                if (selectedRecord != null) {
                    showRecordDetail(selectedRecord)
                }
            }
        }
        
        return JBScrollPane(recordTable)
    }

    /**
     * Creates the detail panel with Raw and Parsed tabs
     * Requirements: 2.4, 6.3
     */
    private fun createDetailPanel(): JComponent {
        // Raw tab
        rawDetailArea.isEditable = false
        rawDetailArea.font = JBUI.Fonts.create("Monospaced", 12)
        detailTabbedPane.addTab("Raw", JBScrollPane(rawDetailArea))
        
        // Parsed tab
        parsedDetailPanel.add(JBLabel("选择一条记录查看详情"), BorderLayout.CENTER)
        detailTabbedPane.addTab("Parsed", JBScrollPane(parsedDetailPanel))
        
        return detailTabbedPane
    }

    /**
     * Setup event listeners for buttons
     */
    private fun setupEventListeners() {
        startButton.addActionListener { startRecording() }
        stopButton.addActionListener { stopRecording() }
        refreshButton.addActionListener { refreshRecordList() }
        historyManageButton.addActionListener { openHistoryManagementDialog() }
    }
    
    /**
     * 打开历史记录管理对话框
     * 
     * Requirements: 5.1
     */
    private fun openHistoryManagementDialog() {
        val dialog = HistoryManagementDialog(project)
        dialog.show()
    }

    /**
     * Setup bridge listener to sync tt command results from query editor
     * 
     * 监听 ArthasBridgeListener.onFinish 事件，检测命令是否为 tt 命令（以 "tt " 开头），
     * 如果是 tt -t 命令，自动刷新记录列表。
     * 
     * Requirements: 7.2, 7.3
     */
    private fun setupBridgeListener() {
        val template = executionManager.getTemplate(jvm, tabId)
        if (template == null) {
            // Template not available yet, this shouldn't happen in normal flow
            return
        }
        
        template.addListener(object : ArthasBridgeListener() {
            override fun onFinish(command: String, result: ArthasResultItem, rawContent: String) {
                // Check if this is a tt command (starts with "tt ")
                // Requirements: 7.2 - WHEN the user types a `tt -t` command in the query editor THEN the ArthasUI SHALL sync the results to the TimeTunnel panel
                if (command.trim().startsWith("tt ")) {
                    ApplicationManager.getApplication().invokeLater {
                        handleTtCommandResult(command, rawContent)
                    }
                }
            }
        })
    }

    /**
     * Handle tt command result from query editor
     * 
     * 根据命令类型处理不同的输出：
     * - tt -t: 记录命令，自动刷新列表
     * - tt -l: 列表命令，使用 TtOutputParser.parseList() 解析并更新表格
     * - tt -i: 详情命令，使用 TtOutputParser.parseDetail() 解析并更新详情面板
     * - tt -p: 重放命令，使用 TtOutputParser.parseReplayResult() 解析并显示结果
     */
    private fun handleTtCommandResult(command: String, rawContent: String) {
        when {
            command.contains("-t ") -> {
                // tt -t command: 记录命令完成后自动刷新列表
                refreshRecordList()
            }
            command.contains("-l") -> {
                // tt -l command: 使用 TtOutputParser.parseList() 解析结果
                val parsedRecords = TtOutputParser.parseList(rawContent, currentSessionId)
                updateRecordList(parsedRecords)
            }
            command.contains("-i ") -> {
                // tt -i command: 使用 TtOutputParser.parseDetail() 解析详情
                val indexMatch = Regex("-i\\s+(\\d+)").find(command)
                val index = indexMatch?.groupValues?.get(1)?.toIntOrNull()
                if (index != null) {
                    val baseRecord = records.find { it.index == index }
                    if (baseRecord != null) {
                        val detailedRecord = TtOutputParser.parseDetail(rawContent, baseRecord)
                        // 更新记录列表中的记录
                        val recordIndex = records.indexOfFirst { it.index == index }
                        if (recordIndex >= 0) {
                            records[recordIndex] = detailedRecord
                        }
                        // 更新详情面板
                        updateDetailPanel(detailedRecord)
                        // 同时更新 raw 输出
                        rawDetailArea.text = rawContent
                        rawDetailArea.caretPosition = 0
                    }
                }
            }
            command.contains("-p ") -> {
                // tt -p command: 使用 TtOutputParser.parseReplayResult() 解析重放结果
                val replayResult = TtOutputParser.parseReplayResult(rawContent)
                showReplayResult(replayResult, rawContent)
            }
        }
    }
    
    /**
     * 显示重放结果
     */
    private fun showReplayResult(result: TtReplayResult, rawContent: String) {
        val message = if (result.success) {
            buildString {
                appendLine("重放成功！")
                appendLine("耗时: ${result.costMs}ms")
                result.returnValue?.let { appendLine("返回值: $it") }
            }
        } else {
            buildString {
                appendLine("重放失败")
                result.exception?.let { appendLine("异常: $it") }
            }
        }
        
        // 更新详情面板显示重放结果
        rawDetailArea.text = rawContent
        rawDetailArea.caretPosition = 0
        
        // 显示结果对话框
        JOptionPane.showMessageDialog(
            this,
            message,
            if (result.success) "重放成功" else "重放失败",
            if (result.success) JOptionPane.INFORMATION_MESSAGE else JOptionPane.ERROR_MESSAGE
        )
    }

    /**
     * Start recording method calls
     * 
     * 验证类名和方法名非空，生成命令 `tt -t <className> <methodName> -n <count>`，
     * 通过 ArthasBridgeTemplate.execute() 执行，并更新 UI 状态。
     * 
     * Requirements: 1.3, 1.4
     */
    fun startRecording() {
        val className = classNameField.text.trim()
        val methodName = methodNameField.text.trim()
        
        // 验证类名和方法名非空
        if (className.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "请输入类名",
                "输入错误",
                JOptionPane.WARNING_MESSAGE
            )
            classNameField.requestFocus()
            return
        }
        
        if (methodName.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "请输入方法名",
                "输入错误",
                JOptionPane.WARNING_MESSAGE
            )
            methodNameField.requestFocus()
            return
        }
        
        val recordCount = recordCountSpinner.value as Int
        
        // 生成新的 sessionId
        currentSessionId = historyService.generateSessionId()
        
        // 生成命令: tt -t <class> <method> -n <count>
        val command = buildTtRecordCommand(className, methodName, recordCount)
        
        // 更新 UI 状态：禁用开始按钮，启用停止按钮，显示"正在记录"
        updateRecordingState(true, 0)
        
        // 记录当前捕获数量
        capturedCount = 0
        
        // 通过 ArthasBridgeTemplate.execute() 执行
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val template = executionManager.getTemplate(jvm, tabId)
                template?.execute(command)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "执行命令失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    // 记录完成，更新 UI 状态
                    updateRecordingState(false, 0)
                    
                    // 自动刷新列表
                    refreshRecordList()
                }
            }
        }
    }
    
    /**
     * 构建 TT 记录命令
     * 
     * @param className 类名
     * @param methodName 方法名
     * @param count 记录次数
     * @return 格式化的命令字符串
     */
    private fun buildTtRecordCommand(className: String, methodName: String, count: Int): String {
        return "tt -t $className $methodName -n $count"
    }
    
    /**
     * 更新记录状态的 UI
     * 
     * @param recording 是否正在记录
     * @param count 当前捕获数量
     */
    private fun updateRecordingState(recording: Boolean, count: Int) {
        isRecording = recording
        startButton.isEnabled = !recording
        stopButton.isEnabled = recording
        classNameField.isEnabled = !recording
        methodNameField.isEnabled = !recording
        recordCountSpinner.isEnabled = !recording
        
        if (recording) {
            statusLabel.text = if (count > 0) "正在记录 ($count)" else "正在记录..."
            statusLabel.icon = AllIcons.Process.Step_1
        } else {
            statusLabel.text = "空闲"
            statusLabel.icon = null
        }
    }

    /**
     * Stop recording
     * 
     * 调用 ArthasExecutionManager.cancelCurrentCommand() 取消当前命令，
     * 并更新 UI 状态：启用开始按钮，禁用停止按钮，显示"空闲"
     * 
     * Requirements: 1.5
     */
    fun stopRecording() {
        // 调用 ArthasExecutionManager.cancelCurrentCommand() 取消当前命令
        val cancelled = executionManager.cancelCurrentCommand(jvm, tabId)
        
        // 更新 UI 状态：启用开始按钮，禁用停止按钮，显示"空闲"
        updateRecordingState(false, 0)
        
        if (cancelled) {
            // 自动刷新列表以显示已捕获的记录
            refreshRecordList()
        }
    }

    /**
     * Refresh record list by executing tt -l
     * 
     * 执行 `tt -l` 命令，使用 TtOutputParser.parseList() 解析结果，
     * 并更新表格数据。
     * 
     * Requirements: 2.5
     */
    fun refreshRecordList() {
        // 禁用刷新按钮防止重复点击
        refreshButton.isEnabled = false
        
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val template = executionManager.getTemplate(jvm, tabId)
                if (template == null) {
                    ApplicationManager.getApplication().invokeLater {
                        refreshButton.isEnabled = true
                        JOptionPane.showMessageDialog(
                            this,
                            "未连接到 Arthas",
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                    return@executeOnPooledThread
                }
                
                // 执行 tt -l 命令
                // 注意：结果会通过 bridge listener 的 onFinish 回调处理
                template.execute("tt -l")
                
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "刷新失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } finally {
                ApplicationManager.getApplication().invokeLater {
                    refreshButton.isEnabled = true
                }
            }
        }
    }

    /**
     * Update record list with parsed records
     */
    private fun updateRecordList(newRecords: List<TtRecordItem>) {
        records.clear()
        records.addAll(newRecords)
        recordTableModel.fireTableDataChanged()
        
        // Save records to history
        for (record in newRecords) {
            historyService.saveTtRecord(jvm, record)
        }
    }

    /**
     * Show record detail
     * 
     * 表格行选中时触发，执行 `tt -i <index>` 获取详情，
     * 使用 TtOutputParser.parseDetail() 解析，并更新详情面板。
     * 
     * Requirements: 2.4
     */
    fun showRecordDetail(record: TtRecordItem) {
        // 立即显示基本信息
        ApplicationManager.getApplication().invokeLater {
            updateDetailPanel(record)
        }
        
        // 执行 tt -i <index> 获取详情
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val template = executionManager.getTemplate(jvm, tabId)
                if (template == null) {
                    ApplicationManager.getApplication().invokeLater {
                        rawDetailArea.text = "未连接到 Arthas"
                    }
                    return@executeOnPooledThread
                }
                
                // 执行命令获取详情
                // 结果会通过 bridge listener 的 onFinish 回调处理
                template.execute("tt -i ${record.index}")
                
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    rawDetailArea.text = "获取详情失败: ${e.message}"
                }
            }
        }
    }

    /**
     * Update detail panel with record info
     */
    private fun updateDetailPanel(record: TtRecordItem) {
        // Update raw tab
        val rawText = buildString {
            appendLine("INDEX: ${record.index}")
            appendLine("TIMESTAMP: ${DATE_FORMAT.format(Date(record.timestamp))}")
            appendLine("CLASS: ${record.className}")
            appendLine("METHOD: ${record.methodName}")
            appendLine("COST(ms): ${record.costMs}")
            appendLine("IS-RETURN: ${record.isReturn}")
            appendLine("IS-EXCEPTION: ${record.isException}")
            appendLine("SESSION-ID: ${record.sessionId}")
            record.parameters?.let { appendLine("\nPARAMETERS:\n$it") }
            record.returnValue?.let { appendLine("\nRETURN-VALUE:\n$it") }
            record.exceptionInfo?.let { appendLine("\nEXCEPTION:\n$it") }
        }
        rawDetailArea.text = rawText
        rawDetailArea.caretPosition = 0
        
        // Update parsed tab
        parsedDetailPanel.removeAll()
        if (record.parameters != null || record.returnValue != null) {
            // TODO: Parse and display using OgnlTreeDisplayUI when we have proper ArthasResultItem
            parsedDetailPanel.add(JBLabel("详情解析中..."), BorderLayout.CENTER)
        } else {
            parsedDetailPanel.add(JBLabel("无详细数据，请点击记录获取详情"), BorderLayout.CENTER)
        }
        parsedDetailPanel.revalidate()
        parsedDetailPanel.repaint()
    }

    /**
     * Replay a record
     * 
     * 检查 canReplay()，不可重放时显示提示，
     * 执行 `tt -p -i <index>` 命令，显示重放结果。
     * 
     * Requirements: 3.1, 3.4, 3.5
     */
    fun replayRecord(index: Int) {
        val record = records.find { it.index == index }
        if (record == null) {
            JOptionPane.showMessageDialog(
                this,
                "记录不存在",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        
        // 检查 canReplay()，不可重放时显示提示
        if (!record.canReplay(currentSessionId)) {
            JOptionPane.showMessageDialog(
                this,
                "此记录来自历史会话，无法重放。\n只有当前会话的记录才能重放。\n\n" +
                "提示：TT 索引在 Arthas 重启后会失效，需要重新记录方法调用。",
                "无法重放",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
        // 执行 tt -p -i <index> 命令
        val command = buildReplayCommand(index)
        
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val template = executionManager.getTemplate(jvm, tabId)
                if (template == null) {
                    ApplicationManager.getApplication().invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "未连接到 Arthas",
                            "错误",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                    return@executeOnPooledThread
                }
                
                // 执行重放命令
                // 结果会通过 bridge listener 的 onFinish 回调处理
                template.execute(command)
                
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "重放失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }
    
    /**
     * 构建重放命令
     * 
     * @param index TT 索引
     * @return 格式化的重放命令
     */
    private fun buildReplayCommand(index: Int): String {
        return "tt -p -i $index"
    }
    
    /**
     * 构建多次重放命令
     * 
     * @param index TT 索引
     * @param times 重放次数
     * @param intervalMs 重放间隔（毫秒）
     * @return 格式化的重放命令
     */
    private fun buildMultipleReplayCommand(index: Int, times: Int, intervalMs: Long): String {
        return "tt -p -i $index --replay-times $times --replay-interval $intervalMs"
    }
    
    /**
     * 多次重放记录
     * 
     * @param index TT 索引
     * @param times 重放次数
     * @param intervalMs 重放间隔（毫秒）
     */
    fun replayMultiple(index: Int, times: Int, intervalMs: Long) {
        val record = records.find { it.index == index }
        if (record == null) {
            JOptionPane.showMessageDialog(
                this,
                "记录不存在",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }
        
        // 检查 canReplay()
        if (!record.canReplay(currentSessionId)) {
            JOptionPane.showMessageDialog(
                this,
                "此记录来自历史会话，无法重放。",
                "无法重放",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
        val command = buildMultipleReplayCommand(index, times, intervalMs)
        
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val template = executionManager.getTemplate(jvm, tabId)
                template?.execute(command)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "重放失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }

    // ==================== AdditionalTabComponent Implementation ====================

    override fun dispose() {
        // Cleanup resources
    }

    override fun getPreferredFocusableComponent(): JComponent {
        return classNameField
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
        return "TimeTunnel"
    }

    // ==================== Table Model ====================

    /**
     * Table model for TT records
     * Requirements: 2.1
     */
    private inner class TtRecordTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = records.size

        override fun getColumnCount(): Int = COLUMN_NAMES.size

        override fun getColumnName(column: Int): String = COLUMN_NAMES[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val record = records.getOrNull(rowIndex) ?: return null
            return when (columnIndex) {
                COL_INDEX -> record.index
                COL_TIMESTAMP -> DATE_FORMAT.format(Date(record.timestamp))
                COL_CLASS_METHOD -> "${record.className}.${record.methodName}"
                COL_COST -> record.costMs
                COL_STATUS -> record
                COL_ACTION -> record
                else -> null
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            return columnIndex == COL_ACTION
        }
    }

    // ==================== Cell Renderers ====================

    /**
     * Status column renderer with icons
     * Requirements: 2.2, 2.3
     */
    private inner class StatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): java.awt.Component {
            val component = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column)
            
            if (value is TtRecordItem) {
                icon = when {
                    value.isException -> AllIcons.RunConfigurations.TestFailed
                    value.isReturn -> AllIcons.RunConfigurations.TestPassed
                    else -> AllIcons.RunConfigurations.TestUnknown
                }
                text = ""
                horizontalAlignment = SwingConstants.CENTER
            }
            
            return component
        }
    }

    /**
     * Action column renderer with replay button
     */
    private inner class ActionCellRenderer : DefaultTableCellRenderer() {
        private val button = JButton("重放")
        
        init {
            button.icon = AllIcons.Actions.Redo
        }
        
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): java.awt.Component {
            if (value is TtRecordItem) {
                button.isEnabled = value.canReplay(currentSessionId)
                button.toolTipText = if (button.isEnabled) "重放此调用" else "历史会话记录无法重放"
            }
            return button
        }
    }

    /**
     * Action column editor for replay button click
     */
    private inner class ActionCellEditor : AbstractCellEditor(), javax.swing.table.TableCellEditor {
        private val button = JButton("重放")
        private var currentRecord: TtRecordItem? = null
        
        init {
            button.icon = AllIcons.Actions.Redo
            button.addActionListener {
                currentRecord?.let { record ->
                    replayRecord(record.index)
                }
                fireEditingStopped()
            }
        }
        
        override fun getTableCellEditorComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int
        ): java.awt.Component {
            currentRecord = value as? TtRecordItem
            button.isEnabled = currentRecord?.canReplay(currentSessionId) == true
            return button
        }
        
        override fun getCellEditorValue(): Any? = currentRecord
    }
}
