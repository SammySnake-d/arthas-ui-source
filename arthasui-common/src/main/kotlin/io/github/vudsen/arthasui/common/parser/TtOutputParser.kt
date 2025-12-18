package io.github.vudsen.arthasui.common.parser

import io.github.vudsen.arthasui.api.bean.TtRecordItem
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * TT 重放结果
 */
data class TtReplayResult(
    /** 是否成功 */
    val success: Boolean,
    /** 返回值 */
    val returnValue: String? = null,
    /** 异常信息 */
    val exception: String? = null,
    /** 耗时(ms) */
    val costMs: Long = 0
)

/**
 * 解析 tt 命令的各种输出格式
 * 
 * 支持解析:
 * - tt -l: 列表输出
 * - tt -i <index>: 详情输出
 * - tt -p: 重放结果
 */
object TtOutputParser {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    // tt -l 输出的表头分隔线模式
    private val HEADER_SEPARATOR_PATTERN = Regex("^-{10,}$")
    
    // tt -l 输出的数据行模式
    // INDEX   TIMESTAMP            COST(ms)  IS-RET  IS-EXP   OBJECT         CLASS                          METHOD
    private val LIST_ROW_PATTERN = Regex(
        """^\s*(\d+)\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\s+(\d+)\s+(true|false)\s+(true|false)\s+(0x[0-9a-fA-F]+|null)\s+(\S+)\s+(\S+)\s*$"""
    )
    
    // tt -i 输出的键值对模式
    private val DETAIL_KEY_VALUE_PATTERN = Regex("""^\s*([A-Z][A-Z0-9_-]*(?:\([^)]*\))?)\s+(.*)$""")

    /**
     * 解析 tt -l 输出，返回记录列表
     * 
     * 输入格式:
     *  INDEX   TIMESTAMP            COST(ms)  IS-RET  IS-EXP   OBJECT         CLASS                          METHOD
     * -------------------------------------------------------------------------------------------------------------------------------------
     *  1000    2024-01-15 10:30:45  23        true    false    0x12345678     com.example.UserService        login
     *
     * @param output tt -l 命令的输出
     * @param sessionId 当前会话 ID
     * @return 解析出的记录列表，解析失败返回空列表
     */
    fun parseList(output: String, sessionId: String): List<TtRecordItem> {
        if (output.isBlank()) {
            return emptyList()
        }
        
        return try {
            val records = mutableListOf<TtRecordItem>()
            val lines = output.lines()
            var headerFound = false
            
            for (line in lines) {
                // 跳过空行
                if (line.isBlank()) continue
                
                // 检测表头分隔线
                if (HEADER_SEPARATOR_PATTERN.matches(line.trim())) {
                    headerFound = true
                    continue
                }
                
                // 跳过表头行（在分隔线之前）
                if (!headerFound) continue
                
                // 解析数据行
                val match = LIST_ROW_PATTERN.matchEntire(line)
                if (match != null) {
                    val (indexStr, timestampStr, costStr, isReturnStr, isExceptionStr, _, className, methodName) = match.destructured
                    
                    val timestamp = try {
                        DATE_FORMAT.parse(timestampStr)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                    
                    records.add(
                        TtRecordItem(
                            index = indexStr.toInt(),
                            timestamp = timestamp,
                            className = className,
                            methodName = methodName,
                            costMs = costStr.toLong(),
                            isReturn = isReturnStr.toBoolean(),
                            isException = isExceptionStr.toBoolean(),
                            sessionId = sessionId
                        )
                    )
                }
            }
            
            records
        } catch (e: Exception) {
            // 解析失败返回空列表，不抛出异常
            emptyList()
        }
    }

    /**
     * 解析 tt -i <index> 输出，返回详细信息
     * 
     * 输入格式:
     *  INDEX          1001
     *  GMT-CREATE     2024-01-15 10:30:46
     *  COST(ms)       15
     *  OBJECT         0x12345678
     *  CLASS          com.example.UserService
     *  METHOD         login
     *  IS-RETURN      true
     *  IS-EXCEPTION   false
     *  PARAMETERS[0]  @String[username]
     *  PARAMETERS[1]  @String[password]
     *  RETURN-OBJ     @User[...]
     *  THROW-EXCEPTION null
     *
     * @param output tt -i 命令的输出
     * @param baseRecord 基础记录（从 tt -l 解析得到）
     * @return 包含详情的记录，解析失败返回原始记录
     */
    fun parseDetail(output: String, baseRecord: TtRecordItem): TtRecordItem {
        if (output.isBlank()) {
            return baseRecord
        }
        
        return try {
            val details = mutableMapOf<String, String>()
            val lines = output.lines()
            val parametersBuilder = StringBuilder()
            var returnValue: String? = null
            var exceptionInfo: String? = null
            
            for (line in lines) {
                if (line.isBlank()) continue
                
                val match = DETAIL_KEY_VALUE_PATTERN.matchEntire(line)
                if (match != null) {
                    val (key, value) = match.destructured
                    details[key.trim()] = value.trim()
                    
                    // 收集参数
                    when {
                        key.startsWith("PARAMETERS") -> {
                            if (parametersBuilder.isNotEmpty()) {
                                parametersBuilder.append("\n")
                            }
                            parametersBuilder.append("$key: $value")
                        }
                        key == "RETURN-OBJ" -> {
                            returnValue = if (value == "null") null else value
                        }
                        key == "THROW-EXCEPTION" -> {
                            exceptionInfo = if (value == "null") null else value
                        }
                    }
                }
            }
            
            baseRecord.copy(
                parameters = if (parametersBuilder.isEmpty()) null else parametersBuilder.toString(),
                returnValue = returnValue,
                exceptionInfo = exceptionInfo
            )
        } catch (e: Exception) {
            // 解析失败返回原始记录
            baseRecord
        }
    }

    /**
     * 解析 tt -p 重放输出
     * 
     * 输入格式（成功）:
     * RE-INDEX       1001
     * GMT-REPLAY     2024-01-15 10:35:00
     * OBJECT         0x12345678
     * CLASS          com.example.UserService
     * METHOD         login
     * IS-RETURN      true
     * IS-EXCEPTION   false
     * COST(ms)       12
     * RETURN-OBJ     @User[...]
     * 
     * 输入格式（失败）:
     * RE-INDEX       1001
     * GMT-REPLAY     2024-01-15 10:35:00
     * OBJECT         0x12345678
     * CLASS          com.example.UserService
     * METHOD         login
     * IS-RETURN      false
     * IS-EXCEPTION   true
     * COST(ms)       5
     * THROW-EXCEPTION java.lang.RuntimeException: error message
     *
     * @param output tt -p 命令的输出
     * @return 重放结果
     */
    fun parseReplayResult(output: String): TtReplayResult {
        if (output.isBlank()) {
            return TtReplayResult(success = false, exception = "Empty output")
        }
        
        return try {
            val details = mutableMapOf<String, String>()
            val lines = output.lines()
            
            for (line in lines) {
                if (line.isBlank()) continue
                
                val match = DETAIL_KEY_VALUE_PATTERN.matchEntire(line)
                if (match != null) {
                    val (key, value) = match.destructured
                    details[key.trim()] = value.trim()
                }
            }
            
            val isReturn = details["IS-RETURN"]?.toBoolean() ?: false
            val isException = details["IS-EXCEPTION"]?.toBoolean() ?: false
            val costMs = details["COST(ms)"]?.toLongOrNull() ?: 0L
            val returnValue = details["RETURN-OBJ"]?.takeIf { it != "null" }
            val exception = details["THROW-EXCEPTION"]?.takeIf { it != "null" }
            
            TtReplayResult(
                success = isReturn && !isException,
                returnValue = returnValue,
                exception = exception,
                costMs = costMs
            )
        } catch (e: Exception) {
            // 解析失败返回失败结果
            TtReplayResult(success = false, exception = "Parse error: ${e.message}")
        }
    }
}
