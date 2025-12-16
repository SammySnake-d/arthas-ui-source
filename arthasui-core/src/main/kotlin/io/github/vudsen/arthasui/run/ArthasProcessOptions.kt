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
     * Unique identifier for the tab, used to support multiple console windows per JVM.
     */
    var tabId: String? = null
    
    /**
     * Display name for the console window, typically the tab name.
     */
    var displayName: String? = null
}