package io.github.vudsen.arthasui.api.conf

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.concurrent.ConcurrentHashMap

/**
 * 命令历史记录
 */
data class CommandHistoryRecord(
    var command: String = "",
    var timestamp: Long = 0,
    var success: Boolean = true
)

/**
 * TT 记录（持久化版本，不含大字段）
 */
data class PersistedTtRecord(
    var index: Int = 0,
    var timestamp: Long = 0,
    var className: String = "",
    var methodName: String = "",
    var costMs: Long = 0,
    var isReturn: Boolean = true,
    var isException: Boolean = false,
    var sessionId: String = ""
)

/**
 * 单个主类的历史数据
 */
data class MainClassHistoryData(
    var mainClass: String = "",
    var lastPid: String? = null,
    var lastConnectTime: Long? = null,
    var commandHistory: MutableList<CommandHistoryRecord> = mutableListOf(),
    var ttRecords: MutableList<PersistedTtRecord> = mutableListOf()
)

/**
 * 持久化状态
 */
class ArthasHistoryState(
    var historyByMainClass: MutableMap<String, MainClassHistoryData> = mutableMapOf(),
    var maxCommandHistoryPerClass: Int = 100,
    var maxTtRecordsPerClass: Int = 50
)


/**
 * Arthas 历史记录持久化组件
 * 
 * 基于主类名存储命令历史和 TT 记录，解决进程重启后历史数据丢失的问题
 */
@State(
    name = "io.github.vudsen.arthasui.api.conf.ArthasHistoryPersistent",
    storages = [Storage("ArthasUIHistory.xml")]
)
@Service(Service.Level.APP)
class ArthasHistoryPersistent : PersistentStateComponent<ArthasHistoryState> {

    private var myState = ArthasHistoryState()
    
    // 使用 ConcurrentHashMap 保证线程安全
    private val lock = Any()

    override fun getState(): ArthasHistoryState {
        return myState
    }

    override fun loadState(state: ArthasHistoryState) {
        this.myState = state
    }

    /**
     * 获取指定主类的历史数据
     * 
     * @param mainClass 主类名
     * @return 历史数据，如果不存在则返回 null
     */
    fun getHistory(mainClass: String): MainClassHistoryData? {
        return myState.historyByMainClass[mainClass]
    }

    /**
     * 保存命令执行记录
     * 
     * @param mainClass 主类名
     * @param record 命令记录
     */
    fun saveCommandRecord(mainClass: String, record: CommandHistoryRecord) {
        synchronized(lock) {
            val historyData = myState.historyByMainClass.getOrPut(mainClass) {
                MainClassHistoryData(mainClass = mainClass)
            }
            historyData.commandHistory.add(record)
            
            // 限制记录数量
            while (historyData.commandHistory.size > myState.maxCommandHistoryPerClass) {
                historyData.commandHistory.removeAt(0)
            }
        }
    }

    /**
     * 保存 TT 记录
     * 
     * @param mainClass 主类名
     * @param record TT 记录
     */
    fun saveTtRecord(mainClass: String, record: PersistedTtRecord) {
        synchronized(lock) {
            val historyData = myState.historyByMainClass.getOrPut(mainClass) {
                MainClassHistoryData(mainClass = mainClass)
            }
            historyData.ttRecords.add(record)
            
            // 限制记录数量
            while (historyData.ttRecords.size > myState.maxTtRecordsPerClass) {
                historyData.ttRecords.removeAt(0)
            }
        }
    }

    /**
     * 清除指定主类的历史数据
     * 
     * @param mainClass 主类名
     */
    fun clearHistory(mainClass: String) {
        synchronized(lock) {
            myState.historyByMainClass.remove(mainClass)
        }
    }

    /**
     * 清除所有历史数据
     */
    fun clearAllHistory() {
        synchronized(lock) {
            myState.historyByMainClass.clear()
        }
    }

    /**
     * 获取所有已记录的主类名列表
     * 
     * @return 主类名列表
     */
    fun getAllMainClasses(): List<String> {
        return myState.historyByMainClass.keys.toList()
    }

    /**
     * 获取指定主类的记录总数（命令历史 + TT 记录）
     * 
     * @param mainClass 主类名
     * @return 记录总数
     */
    fun getRecordCount(mainClass: String): Int {
        val historyData = myState.historyByMainClass[mainClass] ?: return 0
        return historyData.commandHistory.size + historyData.ttRecords.size
    }

    /**
     * 更新主类的最后连接信息
     * 
     * @param mainClass 主类名
     * @param pid 进程 ID
     * @param connectTime 连接时间
     */
    fun updateLastConnect(mainClass: String, pid: String, connectTime: Long) {
        synchronized(lock) {
            val historyData = myState.historyByMainClass.getOrPut(mainClass) {
                MainClassHistoryData(mainClass = mainClass)
            }
            historyData.lastPid = pid
            historyData.lastConnectTime = connectTime
        }
    }
}
