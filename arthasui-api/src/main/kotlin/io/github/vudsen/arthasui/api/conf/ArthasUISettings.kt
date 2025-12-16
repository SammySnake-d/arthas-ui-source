package io.github.vudsen.arthasui.api.conf

import com.intellij.openapi.util.SystemInfo
import io.github.vudsen.arthasui.api.DeepCopyable
import io.github.vudsen.arthasui.api.util.deepCopy

class ArthasUISettings(
    /**
     * 宿主机
     */
    var hostMachines: MutableList<HostMachineConfig> = mutableListOf(),
    /**
     * 执行命令快捷键配置
     */
    var executeCommandShortcut: String = defaultShortcut()
) : DeepCopyable<ArthasUISettings> {

    companion object {
        fun defaultShortcut(): String {
            return if (SystemInfo.isMac) {
                "meta R"
            } else {
                "ctrl R"
            }
        }
    }

    override fun deepCopy(): ArthasUISettings {
        return ArthasUISettings(hostMachines.deepCopy(), executeCommandShortcut)
    }

}
