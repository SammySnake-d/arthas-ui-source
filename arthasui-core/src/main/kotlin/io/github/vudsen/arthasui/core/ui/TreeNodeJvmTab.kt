package io.github.vudsen.arthasui.core.ui

import io.github.vudsen.arthasui.api.ui.RecursiveTreeNode
import io.github.vudsen.arthasui.common.ui.AbstractRecursiveTreeNode
import javax.swing.Icon
import javax.swing.JLabel

class TreeNodeJvmTab(
    private val tab: ArthasTab,
    val parentJvm: TreeNodeJVM
) : AbstractRecursiveTreeNode() {

    val tabId: String
        get() = tab.id

    fun displayName(): String {
        return parentJvm.tabDisplayName(tab)
    }

    override fun refresh(): List<AbstractRecursiveTreeNode> {
        return emptyList()
    }

    override fun getIcon(): Icon {
        return parentJvm.jvm.getIcon()
    }

    override fun resolveText(): JLabel {
        return JLabel(tab.name)
    }

    override fun getTopRootNode(): RecursiveTreeNode {
        return parentJvm.getTopRootNode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TreeNodeJvmTab) return false

        if (tab.id != other.tab.id) return false
        if (parentJvm.jvm != other.parentJvm.jvm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tab.id.hashCode()
        result = 31 * result + parentJvm.jvm.hashCode()
        return result
    }

    override fun toString(): String {
        return tab.name
    }
}
