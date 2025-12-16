package io.github.vudsen.arthasui.core.ui

import com.intellij.openapi.ui.shortenTextWithEllipsis
import io.github.vudsen.arthasui.api.conf.JvmProviderConfig
import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.api.ui.RecursiveTreeNode
import io.github.vudsen.arthasui.common.ui.AbstractRecursiveTreeNode
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode

data class ArthasTab(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)

enum class AddTabResult {
    SUCCESS,
    EMPTY,
    DUPLICATE
}

class TreeNodeJVM(
    val providerConfig: JvmProviderConfig,
    val jvm: JVM,
    val ctx: TreeNodeContext,
    val parent: AbstractRecursiveTreeNode
) : AbstractRecursiveTreeNode() {

    private val tabs = CopyOnWriteArrayList<ArthasTab>()

    override fun refresh(): List<AbstractRecursiveTreeNode> {
        return tabs.map { TreeNodeJvmTab(it, this) }
    }

    override fun refreshNode(force: Boolean): DefaultMutableTreeNode {
        // 刷新 JVM 节点时同时刷新整个列表
        parent.refreshNode(force)
        return super.refreshNode(force)
    }

    override fun getIcon(): Icon {
        return jvm.getIcon()
    }

    override fun resolveText(): JLabel {
        val label = JLabel()
        label.text = jvm.id + ' ' + shortenTextWithEllipsis(
            jvm.name,
            4,
            10,
            0.3f,
            ctx.tree.width - 200,
            { s ->
                label.getFontMetrics(label.font).stringWidth(s)
            })
        return label
    }

    override fun getTopRootNode(): RecursiveTreeNode {
        return ctx.root
    }

    fun addTab(name: String): AddTabResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AddTabResult.EMPTY
        }
        if (tabs.any { it.name == trimmed }) {
            return AddTabResult.DUPLICATE
        }
        tabs.add(ArthasTab(name = trimmed))
        return AddTabResult.SUCCESS
    }

    fun removeTab(tabId: String) {
        tabs.removeIf { it.id == tabId }
    }

    fun nextTabName(): String {
        val existingNames = tabs.mapTo(mutableSetOf()) { it.name }
        var index = 1
        while (existingNames.contains("Tab $index")) {
            index++
        }
        return "Tab $index"
    }

    internal fun tabDisplayName(tab: ArthasTab): String {
        return "${tab.name} (${jvm.name})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TreeNodeJVM

        return jvm == other.jvm
    }

    override fun hashCode(): Int {
        return jvm.hashCode()
    }

    override fun toString(): String {
        return jvm.name
    }


}
