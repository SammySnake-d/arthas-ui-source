package io.github.vudsen.arthasui.api

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import io.github.vudsen.arthasui.api.conf.HostMachineConnectConfig
import io.github.vudsen.arthasui.api.conf.JvmProviderConfig
import io.github.vudsen.arthasui.api.bean.VirtualFileAttributes
import io.github.vudsen.arthasui.api.conf.HostMachineConfig

interface ArthasExecutionManager {

    companion object {
        /**
         * 在创建 [com.intellij.openapi.vfs.VirtualFile] 时，添加必要的参数以开启 console
         */
        val VF_ATTRIBUTES = Key.create<VirtualFileAttributes>("VirtualFileAttributes")
    }

    /**
     * 初始化一个 [ArthasBridgeTemplate]
     * @param tabId Unique identifier for the tab, if null uses JVM id
     */
    fun initTemplate(jvm: JVM, hostMachineConfig: HostMachineConfig, providerConfig: JvmProviderConfig, tabId: String? = null): ArthasBridgeTemplate


    /**
     * 是否已经连接过了
     * @param tabId Unique identifier for the tab, if null uses JVM id
     */
    fun isAttached(jvm: JVM, tabId: String? = null): Boolean

    /**
     * 获取已经创建的 [ArthasBridgeTemplate]
     * @param tabId Unique identifier for the tab, if null uses JVM id
     */
    fun getTemplate(jvm: JVM, tabId: String? = null): ArthasBridgeTemplate?

    /**
     * 设置当前正在执行命令的 ProgressIndicator
     * @param jvm JVM instance
     * @param tabId Unique identifier for the tab
     * @param indicator The progress indicator to store, null to clear
     */
    fun setCurrentIndicator(jvm: JVM, tabId: String?, indicator: ProgressIndicator?)

    /**
     * 取消当前正在执行的命令
     * @param jvm JVM instance
     * @param tabId Unique identifier for the tab
     * @return true if a command was cancelled, false if no command was running
     */
    fun cancelCurrentCommand(jvm: JVM, tabId: String?): Boolean

    /**
     * 设置当前正在执行的命令
     * @param jvm JVM instance
     * @param tabId Unique identifier for the tab
     * @param command The command being executed, null to clear
     */
    fun setCurrentCommand(jvm: JVM, tabId: String?, command: String?)

    /**
     * 获取当前正在执行的命令
     * @param jvm JVM instance
     * @param tabId Unique identifier for the tab
     * @return The current command, or null if no command is running
     */
    fun getCurrentCommand(jvm: JVM, tabId: String?): String?

}