package io.github.vudsen.arthasui.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.api.bean.TtRecordItem
import io.github.vudsen.arthasui.api.conf.ArthasHistoryPersistent
import io.github.vudsen.arthasui.api.conf.CommandHistoryRecord
import io.github.vudsen.arthasui.api.conf.PersistedTtRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 历史数据管理服务
 * 
 * 作为 UI 和持久化层之间的桥梁，提供基于主类名的历史数据管理功能。
 * 
 * 主要职责：
 * - 维护 PID 到主类名的映射缓存
 * - 提供命令历史和 TT 记录的保存与查询接口
 * - 生成会话 ID 用于区分不同的 Arthas 会话
 */
@Service(Service.Level.PROJECT)
class ArthasHistoryService(private val project: Project) {

    private val persistent: ArthasHistoryPersistent = service()

    /**
     * PID -> MainClass 映射缓存
     * 用于快速查找 JVM 进程对应的主类名
     */
    private val pidToMainClass = ConcurrentHashMap<String, String>()

    /**
     * 注册 JVM 的 PID 和主类名映射
     * 
     * @param pid 进程 ID
     * @param mainClass 主类名
     */
    fun registerJvm(pid: String, mainClass: String) {
        pidToMainClass[pid] = mainClass
        // 同时更新持久化层的最后连接信息
        persistent.updateLastConnect(mainClass, pid, System.currentTimeMillis())
    }

    /**
     * 根据 PID 获取主类名
     * 
     * @param pid 进程 ID
     * @return 主类名，如果未注册则返回 null
     */
    fun getMainClass(pid: String): String? {
        return pidToMainClass[pid]
    }

    /**
     * 保存命令执行记录
     * 
     * 使用 JVM 的 name 属性（主类名）作为持久化键
     * 
     * @param jvm JVM 实例
     * @param command 执行的命令
     * @param success 命令是否执行成功
     * @param output 命令输出（可选，目前不持久化以避免存储过大）
     */
    fun saveCommand(jvm: JVM, command: String, success: Boolean, output: String?) {
        val mainClass = jvm.name
        val record = CommandHistoryRecord(
            command = command,
            timestamp = System.currentTimeMillis(),
            success = success
        )
        persistent.saveCommandRecord(mainClass, record)
    }

    /**
     * 保存 TT 记录
     * 
     * 将 TtRecordItem 转换为 PersistedTtRecord 并持久化
     * 
     * @param jvm JVM 实例
     * @param record TT 记录
     */
    fun saveTtRecord(jvm: JVM, record: TtRecordItem) {
        val mainClass = jvm.name
        val persistedRecord = PersistedTtRecord(
            index = record.index,
            timestamp = record.timestamp,
            className = record.className,
            methodName = record.methodName,
            costMs = record.costMs,
            isReturn = record.isReturn,
            isException = record.isException,
            sessionId = record.sessionId
        )
        persistent.saveTtRecord(mainClass, persistedRecord)
    }

    /**
     * 获取命令历史
     * 
     * @param mainClass 主类名
     * @return 命令历史记录列表，如果没有历史则返回空列表
     */
    fun getCommandHistory(mainClass: String): List<CommandHistoryRecord> {
        return persistent.getHistory(mainClass)?.commandHistory ?: emptyList()
    }

    /**
     * 获取 TT 记录历史
     * 
     * @param mainClass 主类名
     * @return TT 记录列表，如果没有历史则返回空列表
     */
    fun getTtRecords(mainClass: String): List<PersistedTtRecord> {
        return persistent.getHistory(mainClass)?.ttRecords ?: emptyList()
    }

    /**
     * 生成新的会话 ID
     * 
     * 每次开始新的 TT 记录会话时调用，用于区分不同会话的记录
     * 只有当前会话的记录才能重放
     * 
     * @return 唯一的会话 ID
     */
    fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * 清除指定主类的历史数据
     * 
     * @param mainClass 主类名
     */
    fun clearHistory(mainClass: String) {
        persistent.clearHistory(mainClass)
    }

    /**
     * 清除所有历史数据
     */
    fun clearAllHistory() {
        persistent.clearAllHistory()
    }

    /**
     * 获取所有已记录的主类名列表
     * 
     * @return 主类名列表
     */
    fun getAllMainClasses(): List<String> {
        return persistent.getAllMainClasses()
    }

    /**
     * 获取指定主类的记录总数
     * 
     * @param mainClass 主类名
     * @return 记录总数（命令历史 + TT 记录）
     */
    fun getRecordCount(mainClass: String): Int {
        return persistent.getRecordCount(mainClass)
    }

    /**
     * 获取指定主类的唯一 className + methodName 组合列表
     * 
     * 用于最近记录快速选择功能，从历史 TT 记录中提取唯一的类名和方法名组合
     * 
     * Requirements: 7.4 - WHERE the user has previously recorded a class-method combination 
     * THEN the ArthasUI SHALL provide a dropdown with recent recordings for quick selection
     * 
     * @param mainClass 主类名（JVM 的主类）
     * @return 唯一的 className + methodName 组合列表，按最近使用时间排序（最近的在前）
     */
    fun getRecentRecordings(mainClass: String): List<Pair<String, String>> {
        val ttRecords = getTtRecords(mainClass)
        if (ttRecords.isEmpty()) {
            return emptyList()
        }
        
        // 按时间戳降序排序，然后提取唯一的 className + methodName 组合
        // 使用 LinkedHashSet 保持插入顺序（最近的在前）
        val uniqueCombinations = LinkedHashSet<Pair<String, String>>()
        ttRecords.sortedByDescending { it.timestamp }
            .forEach { record ->
                uniqueCombinations.add(Pair(record.className, record.methodName))
            }
        
        return uniqueCombinations.toList()
    }
}
