package io.github.vudsen.arthasui.run

import com.intellij.execution.configurations.RunConfigurationOptions
import io.github.vudsen.arthasui.api.JVM

class ArthasProcessOptions : RunConfigurationOptions() {
    /**
     * 为了提供空参构造器，只能使用 lateinit，但是在创建后必须提供这个参数
     * @see com.intellij.execution.configurations.RunConfigurationBase.loadState
     */
    lateinit var jvm: JVM
    
    /**
     * 唯一标识每个控制台的 ID，每个 tab 都有独立的控制台进程
     */
    lateinit var tabId: String
}