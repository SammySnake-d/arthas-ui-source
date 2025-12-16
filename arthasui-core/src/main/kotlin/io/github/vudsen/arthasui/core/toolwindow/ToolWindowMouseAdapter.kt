package io.github.vudsen.arthasui.core.toolwindow

import io.github.vudsen.arthasui.core.ui.TreeNodeJVM
import io.github.vudsen.arthasui.core.ui.TreeNodeJvmTab
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class ToolWindowMouseAdapter(private val toolwindow: ToolWindowTree) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        val uo = toolwindow.currentFocusedNode() ?: return
        if (e.clickCount == 2 && (uo is TreeNodeJVM || uo is TreeNodeJvmTab)) {
            toolwindow.tryOpenQueryConsole(uo)
        }
    }

}
