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
     * @param tabId 唯一标识每个控制台的 ID，每个 tab 都有独立的控制台进程
     */
    fun initTemplate(jvm: JVM, hostMachineConfig: HostMachineConfig, providerConfig: JvmProviderConfig, tabId: String): ArthasBridgeTemplate


    /**
     * 是否已经连接过了
     * @param tabId 唯一标识每个控制台的 ID
     */
    fun isAttached(jvm: JVM, tabId: String): Boolean

    /**
     * 获取已经创建的 [ArthasBridgeTemplate]
     * @param tabId 唯一标识每个控制台的 ID
     */
    fun getTemplate(jvm: JVM, tabId: String): ArthasBridgeTemplate?


}