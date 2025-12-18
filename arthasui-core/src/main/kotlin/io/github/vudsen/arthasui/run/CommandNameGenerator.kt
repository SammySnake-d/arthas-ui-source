package io.github.vudsen.arthasui.run

/**
 * 根据 Arthas 命令生成简短的显示名称。
 * 
 * 名称格式：`命令:类名.方法名` 或 `命令:类名`
 * 
 * 示例：
 * - `watch com.example.UserService login` -> `watch:UserService.login`
 * - `trace com.example.OrderService *` -> `trace:OrderService.*`
 * - `jad com.example.MyClass` -> `jad:MyClass`
 * - `ognl @System@getProperty("user.home")` -> `ognl:System.getProperty`
 */
object CommandNameGenerator {
    
    /**
     * 根据命令生成简短名称
     */
    fun generate(command: String): String {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return "unknown"
        
        val cmd = parts[0].lowercase()
        val nonOptionParts = parts.drop(1).filter { !it.startsWith("-") }
        
        return when (cmd) {
            "watch", "trace", "stack", "monitor", "tt" -> {
                // 格式: command class method
                val className = nonOptionParts.getOrNull(0)?.let { simplifyClassName(it) } ?: "?"
                val method = nonOptionParts.getOrNull(1) ?: "*"
                "$cmd:$className.$method"
            }
            "jad", "sc", "dump", "redefine", "retransform" -> {
                // 格式: command class
                val className = nonOptionParts.getOrNull(0)?.let { simplifyClassName(it) } ?: "?"
                "$cmd:$className"
            }
            "sm" -> {
                // 格式: sm class [method]
                val className = nonOptionParts.getOrNull(0)?.let { simplifyClassName(it) } ?: "?"
                val method = nonOptionParts.getOrNull(1)
                if (method != null) "$cmd:$className.$method" else "$cmd:$className"
            }
            "ognl" -> {
                // 提取 OGNL 表达式中的关键信息
                val expr = nonOptionParts.joinToString(" ").let { cleanQuotes(it) }
                val simplified = simplifyOgnl(expr)
                "$cmd:$simplified"
            }
            "getstatic" -> {
                val className = nonOptionParts.getOrNull(0)?.let { simplifyClassName(it) } ?: "?"
                val field = nonOptionParts.getOrNull(1) ?: "?"
                "$cmd:$className.$field"
            }
            "thread" -> {
                val threadId = nonOptionParts.getOrNull(0)
                if (threadId != null) "$cmd:$threadId" else cmd
            }
            "vmtool" -> {
                var action: String? = null
                var className: String? = null
                for (i in parts.indices) {
                    when {
                        parts[i] == "--action" && i + 1 < parts.size -> action = parts[i + 1]
                        parts[i] == "--className" && i + 1 < parts.size -> className = simplifyClassName(parts[i + 1])
                    }
                }
                when {
                    action != null && className != null -> "$cmd:$action:$className"
                    action != null -> "$cmd:$action"
                    else -> cmd
                }
            }
            else -> {
                // 其他命令直接返回命令名
                cmd
            }
        }
    }
    
    /**
     * 简化类名，只保留简单类名
     * com.example.service.UserService -> UserService
     */
    private fun simplifyClassName(fullClassName: String): String {
        // 处理通配符
        if (fullClassName.contains("*")) {
            val parts = fullClassName.split(".")
            // 找到最后一个非通配符的部分
            val meaningful = parts.filter { it != "*" && it.isNotEmpty() }
            return if (meaningful.isNotEmpty()) {
                val last = meaningful.last()
                if (fullClassName.endsWith("*")) "$last.*" else last
            } else {
                "*"
            }
        }
        // 普通类名，取最后一部分
        return fullClassName.substringAfterLast(".")
    }
    
    /**
     * 简化 OGNL 表达式
     * @System@getProperty("user.home") -> System.getProperty
     * #user.name -> user.name
     */
    private fun simplifyOgnl(expr: String): String {
        // 处理静态方法调用 @Class@method
        val staticMatch = Regex("@([\\w.]+)@(\\w+)").find(expr)
        if (staticMatch != null) {
            val className = simplifyClassName(staticMatch.groupValues[1])
            val method = staticMatch.groupValues[2]
            return "$className.$method"
        }
        
        // 处理变量访问 #var.field
        val varMatch = Regex("#(\\w+)(?:\\.(\\w+))?").find(expr)
        if (varMatch != null) {
            val varName = varMatch.groupValues[1]
            val field = varMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return if (field != null) "$varName.$field" else varName
        }
        
        // 截取前20个字符
        return if (expr.length > 20) expr.take(20) + "..." else expr
    }
    
    /**
     * 移除字符串两端的引号
     */
    private fun cleanQuotes(str: String): String {
        return str.trim().removeSurrounding("'").removeSurrounding("\"")
    }
}
