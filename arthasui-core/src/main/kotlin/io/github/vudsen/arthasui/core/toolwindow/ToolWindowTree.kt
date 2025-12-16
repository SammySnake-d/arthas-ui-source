package io.github.vudsen.arthasui.core.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.treeStructure.Tree
import io.github.vudsen.arthasui.api.ArthasExecutionManager
import io.github.vudsen.arthasui.api.bean.VirtualFileAttributes
import io.github.vudsen.arthasui.api.extension.JvmProviderManager
import io.github.vudsen.arthasui.api.ui.RecursiveTreeNode
import io.github.vudsen.arthasui.common.ui.AbstractRecursiveTreeNode
import io.github.vudsen.arthasui.core.ui.TreeNodeJVM
import io.github.vudsen.arthasui.api.conf.ArthasUISettingsPersistent
import io.github.vudsen.arthasui.api.conf.JvmProviderConfig
import io.github.vudsen.arthasui.api.JVM
import io.github.vudsen.arthasui.common.util.MessagesUtils
import io.github.vudsen.arthasui.common.util.ProgressIndicatorStack
import io.github.vudsen.arthasui.core.ui.DefaultHostMachineTreeNode
import io.github.vudsen.arthasui.core.ui.TreeNodeJvmTab
import io.github.vudsen.arthasui.language.arthas.psi.ArthasFileType
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * ToolWindow 界面。所有的子节点必须实现 [RecursiveTreeNode]. 根节点可以选择实现:
 *
 */
class ToolWindowTree(val project: Project) : Disposable {

    private val logger = Logger.getInstance(ToolWindowTree::class.java)

    private val rootModel = DefaultMutableTreeNode("Invisible Root")

    val tree = Tree(DefaultTreeModel(rootModel))

    private val updateListener =  {
        ApplicationManager.getApplication().invokeLater {
            refreshRootNode(true)
        }
    }

    init {
        tree.setCellRenderer(ToolWindowTreeCellRenderer())
        tree.addMouseListener(ToolWindowRightClickHandler(this))
        tree.addMouseListener(ToolWindowMouseAdapter(this))
        tree.addTreeExpansionListener(ToolWindowExpandListener(this))

        service<ArthasUISettingsPersistent>().addUpdatedListener(updateListener)
        tree.minimumSize = Dimension(301, tree.minimumSize.height)
        refreshRootNode(false)
        tree.expandRow(0)
        tree.isRootVisible = false
    }

    private var isRunning = false

    private data class ConsoleTarget(
        val displayName: String,
        val jvm: JVM,
        val providerConfig: JvmProviderConfig,
        val sourceNode: RecursiveTreeNode,
        val tabId: String? = null
    )

    private fun resolveConsoleTarget(node: RecursiveTreeNode?): ConsoleTarget? {
        return when (node) {
            is TreeNodeJvmTab -> ConsoleTarget(
                node.displayName(),
                node.parentJvm.jvm,
                node.parentJvm.providerConfig,
                node,
                node.tabId
            )
            is TreeNodeJVM -> ConsoleTarget(node.jvm.name, node.jvm, node.providerConfig, node, null)
            else -> null
        }
    }

    /**
     * Handle error from background task, log the error and show error dialog to user.
     */
    private fun handleBackgroundError(error: Throwable, logMessage: String, dialogTitle: String) {
        if (MessagesUtils.isNotAppException(error)) {
            logger.error(logMessage, error)
        }
        val actualMsg = if (error.message == null) {
            error.cause?.message
        } else {
            error.message
        }
        Messages.showErrorDialog(project, actualMsg, dialogTitle)
    }

    /**
     * 刷新某个一个节点
     */
    fun launchRefreshNodeTask(node: RecursiveTreeNode, force: Boolean) {
        if (isRunning) {
            return
        }
        isRunning = true
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Load arthas nodes", true) {

            override fun onFinished() {
                isRunning = false
            }

            override fun run(indicator: ProgressIndicator) {
                ProgressIndicatorStack.push(indicator)
                try {
                    node.refreshNode(force)
                } finally {
                    ProgressIndicatorStack.pop()
                }
                ToolWindowManager.getInstance(project).invokeLater {
                    tree.updateUI()
                }
            }

            override fun onThrowable(error: Throwable) {
                handleBackgroundError(error, "Failed to load nodes", "Load Failed")
            }
        })
    }

    /**
     * 刷新根节点
     */
    fun refreshRootNode(force: Boolean) {
        val persistent = service<ArthasUISettingsPersistent>()

        rootModel.removeAllChildren()
        for (hostMachineConfig in persistent.state.hostMachines) {
            val node: AbstractRecursiveTreeNode = DefaultHostMachineTreeNode(hostMachineConfig, project, tree)
            rootModel.add(node.refreshNode(force))
        }
        tree.model = DefaultTreeModel(rootModel)
        tree.updateUI()
    }

    override fun dispose() {
        service<ArthasUISettingsPersistent>().removeUpdateListener(updateListener)
    }

    fun tryOpenQueryConsole() {
        tryOpenQueryConsole(currentFocusedNode())
    }

    fun tryOpenQueryConsole(target: RecursiveTreeNode?) {
        val consoleTarget = resolveConsoleTarget(target) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Ensure JVM is alive", true) {

            override fun run(indicator: ProgressIndicator) {
                val provider = service<JvmProviderManager>().getProvider(consoleTarget.providerConfig)
                if (provider.isJvmInactive(consoleTarget.jvm)) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showWarningDialog(project, "Jvm ${consoleTarget.jvm.name} is not active, please refresh and try again!", "Jvm Not Available")
                    }
                    return
                }

                val fileEditorManager = FileEditorManager.getInstance(project)

                fileEditorManager.allEditors.find { e -> e.file.fileType == ArthasFileType && e.file.name == consoleTarget.displayName } ?.let {
                    ApplicationManager.getApplication().invokeLater {
                        fileEditorManager.openFile(it.file, true, true)
                    }
                    return
                }

                val topRootNode = consoleTarget.sourceNode.getTopRootNode()
                if (topRootNode !is DefaultHostMachineTreeNode) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Unable to resolve host machine configuration", "Open Console Failed")
                    }
                    return
                }

                val lightVirtualFile = LightVirtualFile(consoleTarget.displayName, ArthasFileType, "")
                lightVirtualFile.putUserData(
                    ArthasExecutionManager.VF_ATTRIBUTES,
                    VirtualFileAttributes(
                        consoleTarget.jvm,
<<<<<<< copilot/fix-subnode-click-error
                        topRootNode.getConnectConfig(),
                        consoleTarget.providerConfig)
=======
                        (consoleTarget.sourceNode.getTopRootNode() as DefaultHostMachineTreeNode).getConnectConfig(),
                        consoleTarget.providerConfig,
                        consoleTarget.tabId,
                        consoleTarget.displayName
                    )
>>>>>>> master
                )
                ApplicationManager.getApplication().invokeLater {
                    fileEditorManager.openFile(lightVirtualFile, true)
                }
            }

            override fun onThrowable(error: Throwable) {
                handleBackgroundError(error, "Failed to open query console", "Open Console Failed")
            }

        })

    }

    /**
     * 获取用户当前聚焦的节点
     */
    fun currentFocusedNode(): RecursiveTreeNode? {
        val comp = tree.lastSelectedPathComponent ?: return null
        if (comp !is DefaultMutableTreeNode) {
            throw IllegalStateException("The node must be a DefaultMutableTreeNode")
        }
        val uo = comp.userObject as RecursiveTreeNode
        return uo
    }

    fun getComponent(): JComponent {
        return ToolWindowToolbar(this).createPanel()
    }

}
