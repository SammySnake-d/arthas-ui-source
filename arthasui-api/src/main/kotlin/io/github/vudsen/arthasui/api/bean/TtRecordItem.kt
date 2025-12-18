package io.github.vudsen.arthasui.api.bean

import io.github.vudsen.arthasui.api.ArthasResultItem

/**
 * TimeTunnel 单条记录
 * 
 * 用于存储 Arthas tt 命令捕获的方法调用信息
 */
data class TtRecordItem(
    /** TT 索引 */
    val index: Int,
    /** 捕获时间戳 */
    val timestamp: Long,
    /** 类名 */
    val className: String,
    /** 方法名 */
    val methodName: String,
    /** 耗时(ms) */
    val costMs: Long,
    /** 是否正常返回 */
    val isReturn: Boolean,
    /** 是否抛出异常 */
    val isException: Boolean,
    /** 会话 ID，用于判断是否可重放 */
    val sessionId: String,
    /** 入参 (详情) */
    val parameters: String? = null,
    /** 返回值 (详情) */
    val returnValue: String? = null,
    /** 异常信息 (详情) */
    val exceptionInfo: String? = null
) : ArthasResultItem {

    /**
     * 判断此记录是否可以重放
     * 只有当前会话的记录才能重放
     * 
     * @param currentSessionId 当前会话 ID
     * @return 如果记录的 sessionId 与当前会话 ID 匹配则返回 true
     */
    fun canReplay(currentSessionId: String): Boolean {
        return sessionId == currentSessionId
    }

    override fun toString(): String {
        return "TtRecordItem(index=$index, timestamp=$timestamp, className='$className', " +
                "methodName='$methodName', costMs=$costMs, isReturn=$isReturn, " +
                "isException=$isException, sessionId='$sessionId')"
    }
}
