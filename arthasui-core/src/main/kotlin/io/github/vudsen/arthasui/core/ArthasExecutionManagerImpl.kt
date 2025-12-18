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

}