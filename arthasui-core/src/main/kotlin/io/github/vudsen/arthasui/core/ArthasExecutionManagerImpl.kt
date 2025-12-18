package io.github.vudsen.arthasui.core

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.rd.util.ConcurrentHashMap
import io.github.vudsen.arthasui.api.*
import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.api.conf.HostMachineConfig
import io.github.vudsen.arthasui.api.conf.JvmProviderConfig
import io.github.vudsen.arthasui.api.extension.JvmProviderManager

/**
 * 协调命令的执行
 */
class ArthasExecutionManagerImpl() : ArthasExecutionManager {


    companion object {

        private val log = Logger.getInstance(ArthasExecutionManagerImpl::class.java)

        private data class ArthasBridgeHolder(
            val arthasBridge: ArthasBridgeTemplate,
            var hostMachineConfig: HostMachineConfig
        )

        /**
         * Composite key for storing bridges, combining JVM and tabId
         */
        private data class BridgeKey(
            val jvmId: String,
            val tabId: String?
        )

    }

    /**
     * 保存所有链接，使用复合键支持同一JVM的多个控制台窗口
     */
    private val bridges = ConcurrentHashMap<BridgeKey, ArthasBridgeHolder>()

    /**
     * 保存当前正在执行命令的 ProgressIndicator
     */
    private val currentIndicators = ConcurrentHashMap<BridgeKey, ProgressIndicator>()

    private fun createKey(jvm: JVM, tabId: String?): BridgeKey {
        return BridgeKey(jvm.id, tabId)
    }

    private fun getHolderAndEnsureAlive(jvm: JVM, tabId: String?): ArthasBridgeHolder? {
        val key = createKey(jvm, tabId)
        bridges[key] ?.let {
            if (it.arthasBridge.isClosed()) {
                bridges.remove(key)
                return null
            }
            return it
        }
        return null
    }


    private fun getOrInitHolder(jvm: JVM, hostMachineConfig: HostMachineConfig, providerConfig: JvmProviderConfig, tabId: String?): ArthasBridgeHolder {
        var holder = getHolderAndEnsureAlive(jvm, tabId)
        if (holder != null) {
            return holder
        }
        val key = createKey(jvm, tabId)
        log.info("Creating new arthas bridge for $jvm with tabId=$tabId")

        val arthasBridgeFactory =
            service<JvmProviderManager>().getProvider(providerConfig).createArthasBridgeFactory(jvm, providerConfig)
        val arthasBridgeTemplate = ArthasBridgeTemplate(arthasBridgeFactory)

        arthasBridgeTemplate.addListener(object : ArthasBridgeListener() {
            override fun onClose() {
                bridges.remove(key)
            }
        })
        holder = ArthasBridgeHolder(arthasBridgeTemplate, hostMachineConfig)

        bridges[key] = holder
        return holder
    }


    /**
     * 初始化一个 [ArthasBridgeTemplate]
     */
    override fun initTemplate(jvm: JVM, hostMachineConfig: HostMachineConfig, providerConfig: JvmProviderConfig, tabId: String?): ArthasBridgeTemplate {
        return getOrInitHolder(jvm, hostMachineConfig, providerConfig, tabId).arthasBridge
    }


    /**
     * 是否已经连接过了
     */
    override fun isAttached(jvm: JVM, tabId: String?): Boolean{
        return getHolderAndEnsureAlive(jvm, tabId) != null
    }

    /**
     * 获取已经创建的 [ArthasBridgeTemplate]，需要先调用 [initTemplate] 来缓存
     */
    override fun getTemplate(jvm: JVM, tabId: String?): ArthasBridgeTemplate? {
        return getHolderAndEnsureAlive(jvm, tabId)?.arthasBridge
    }

    /**
     * 设置当前正在执行命令的 ProgressIndicator
     */
    override fun setCurrentIndicator(jvm: JVM, tabId: String?, indicator: ProgressIndicator?) {
        val key = createKey(jvm, tabId)
        if (indicator == null) {
            currentIndicators.remove(key)
        } else {
            currentIndicators[key] = indicator
        }
    }

    /**
     * 取消当前正在执行的命令
     */
    override fun cancelCurrentCommand(jvm: JVM, tabId: String?): Boolean {
        val key = createKey(jvm, tabId)
        val indicator = currentIndicators[key] ?: return false
        if (!indicator.isCanceled) {
            indicator.cancel()
            return true
        }
        return false
    }

    /**
     * 保存当前正在执行的命令
     */
    private val currentCommands = ConcurrentHashMap<BridgeKey, String>()

    /**
     * 命令完成监听器
     */
    private val commandFinishListeners = ConcurrentHashMap<BridgeKey, MutableList<(String?) -> Unit>>()

    /**
     * 设置当前正在执行的命令
     */
    override fun setCurrentCommand(jvm: JVM, tabId: String?, command: String?) {
        val key = createKey(jvm, tabId)
        if (command == null) {
            val lastCommand = currentCommands.remove(key)
            // 通知所有监听器命令已完成
            notifyCommandFinish(key, lastCommand)
        } else {
            currentCommands[key] = command
        }
    }

    /**
     * 获取当前正在执行的命令
     */
    override fun getCurrentCommand(jvm: JVM, tabId: String?): String? {
        val key = createKey(jvm, tabId)
        return currentCommands[key]
    }

    /**
     * 添加命令完成监听器
     */
    override fun addCommandFinishListener(jvm: JVM, tabId: String?, listener: (String?) -> Unit) {
        val key = createKey(jvm, tabId)
        commandFinishListeners.computeIfAbsent(key) { mutableListOf() }.add(listener)
    }

    /**
     * 移除命令完成监听器
     */
    override fun removeCommandFinishListener(jvm: JVM, tabId: String?, listener: (String?) -> Unit) {
        val key = createKey(jvm, tabId)
        commandFinishListeners[key]?.remove(listener)
    }

    /**
     * 通知命令完成
     */
    private fun notifyCommandFinish(key: BridgeKey, lastCommand: String?) {
        commandFinishListeners[key]?.forEach { listener ->
            try {
                listener(lastCommand)
            } catch (e: Exception) {
                log.warn("Error notifying command finish listener", e)
            }
        }
    }

}