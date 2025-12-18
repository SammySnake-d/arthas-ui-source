package io.github.vudsen.arthasui.core.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import com.intellij.ui.PopupHandler
import io.github.vudsen.arthasui.api.conf.TabContentPersistent
import io.github.vudsen.arthasui.conf.JvmSearchGroupConfigurable
import io.github.vudsen.arthasui.conf.JvmSearchGroupDeleteConfigurable
import io.github.vudsen.arthasui.core.ui.CustomSearchGroupTreeNode
import io.github.vudsen.arthasui.core.ui.DefaultHostMachineTreeNode
import io.github.vudsen.arthasui.core.ui.AddTabResult
import io.github.vudsen.arthasui.core.ui.TreeNodeJVM
import io.github.vudsen.arthasui.core.ui.TreeNodeJvmTab
import java.awt.Component

class ToolWindowRightClickHandler(private val toolWindowTree: ToolWindowTree) : PopupHandler() {

    private fun resolveJvmNode(): TreeNodeJVM? {
        return when (val node = toolWindowTree.currentFocusedNode()) {
            is TreeNodeJVM -> node
            is TreeNodeJvmTab -> node.parentJvm
            else -> null
        }
    }

    private val actions: DefaultActionGroup = DefaultActionGroup().apply {
        add(object : AnAction("Delete") {

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }

            override fun update(e: AnActionEvent) {
                val node = toolWindowTree.currentFocusedNode()
                e.presentation.isEnabled = node is CustomSearchGroupTreeNode || node is TreeNodeJvmTab
            }

            override fun actionPerformed(p0: AnActionEvent) {
                when (val node = toolWindowTree.currentFocusedNode()) {
                    is CustomSearchGroupTreeNode -> {
                        val root = node.getTopRootNode() as DefaultHostMachineTreeNode

                        ShowSettingsUtil.getInstance().editConfigurable(
                            toolWindowTree.project,
                            JvmSearchGroupDeleteConfigurable(toolWindowTree.project, root.config, node.group)
                        )
                    }
                    is TreeNodeJvmTab -> {
                        // Remove persisted content when tab is deleted
                        service<TabContentPersistent>().removeContent(node.tabId)
                        node.parentJvm.removeTab(node.tabId)
                        node.parentJvm.refreshNode(true)
                        toolWindowTree.tree.updateUI()
                    }
                }
            }
        })
        add(object : AnAction("Update") {

            override fun update(e: AnActionEvent) {
                val node = toolWindowTree.currentFocusedNode()
                e.presentation.isEnabled = node is CustomSearchGroupTreeNode || node is TreeNodeJvmTab
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }

            override fun actionPerformed(e: AnActionEvent) {
                when (val node = toolWindowTree.currentFocusedNode()) {
                    is CustomSearchGroupTreeNode -> {
                        val root = node.getTopRootNode() as DefaultHostMachineTreeNode
                        ShowSettingsUtil.getInstance().editConfigurable(
                            toolWindowTree.project,
                            JvmSearchGroupConfigurable(toolWindowTree.project, root.config, node.group)
                        )
                    }
                    is TreeNodeJvmTab -> {
                        val currentName = node.getName()
                        val newName = Messages.showInputDialog(
                            toolWindowTree.project,
                            "Enter new tab name",
                            "Rename Tab",
                            null,
                            currentName,
                            null
                        ) ?: return
                        when (node.parentJvm.updateTabName(node.tabId, newName)) {
                            AddTabResult.EMPTY -> {
                                Messages.showWarningDialog(toolWindowTree.project, "Tab name cannot be empty.", "Cannot Rename Tab")
                                return
                            }
                            AddTabResult.DUPLICATE -> {
                                Messages.showWarningDialog(toolWindowTree.project, "Tab name already exists.", "Cannot Rename Tab")
                                return
                            }
                            AddTabResult.SUCCESS -> {
                                node.parentJvm.refreshNode(true)
                                toolWindowTree.tree.updateUI()
                            }
                        }
                    }
                }
            }
        })
        add(object : AnAction("New Tab", "", AllIcons.General.Add) {

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = resolveJvmNode() != null
            }

            override fun actionPerformed(evt: AnActionEvent) {
                val jvmNode = resolveJvmNode() ?: return
                // 直接使用默认名称创建 tab，不需要用户输入
                val defaultName = jvmNode.nextTabName()
                when (jvmNode.addTab(defaultName)) {
                    AddTabResult.EMPTY, AddTabResult.DUPLICATE -> {
                        // 理论上不会发生，因为 nextTabName() 保证唯一
                        Messages.showWarningDialog(toolWindowTree.project, "Failed to create tab.", "Cannot Create Tab")
                        return
                    }
                    AddTabResult.SUCCESS -> {
                        jvmNode.refreshNode(true)
                        toolWindowTree.tree.updateUI()
                    }
                }
            }
        })
        add(object : AnAction("Create Custom Search Group", "", AllIcons.Nodes.Folder) {
            override fun actionPerformed(evt: AnActionEvent) {
                val rootNode = toolWindowTree.currentFocusedNode()?.getTopRootNode() ?: return
                if (rootNode !is DefaultHostMachineTreeNode) {
                    return
                }
                ShowSettingsUtil.getInstance().editConfigurable(
                    toolWindowTree.project,
                    JvmSearchGroupConfigurable(toolWindowTree.project, rootNode.config)
                )
            }
        })
    }


    override fun invokePopup(p0: Component?, p1: Int, p2: Int) {
        val actionManager = ActionManager.getInstance()
        val popupMenu = actionManager.createActionPopupMenu(ActionPlaces.POPUP, actions)
        popupMenu.component.show(p0, p1, p2)
    }

}
