package io.github.vudsen.arthasui.api.bean

import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.api.conf.HostMachineConfig
import io.github.vudsen.arthasui.api.conf.JvmProviderConfig

data class VirtualFileAttributes(
    val jvm: JVM,
    val hostMachineConfig: HostMachineConfig,
    val providerConfig: JvmProviderConfig,
    /**
     * Unique identifier for the tab, used to support multiple console windows per JVM.
     * If null, uses the JVM id as the identifier (backwards compatible).
     */
    val tabId: String? = null,
    /**
     * Display name for the console window, typically the tab name.
     * If null, uses the JVM name.
     */
    val displayName: String? = null
)

