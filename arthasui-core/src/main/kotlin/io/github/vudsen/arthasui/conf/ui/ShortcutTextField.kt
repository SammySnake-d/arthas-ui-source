package io.github.vudsen.arthasui.conf.ui

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBTextField
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

/**
 * A text field that captures keyboard shortcuts by listening to key events.
 * Users can press a key combination (e.g., Command+R on macOS or Ctrl+R on other systems)
 * and the field will display and store the shortcut in IntelliJ's format.
 *
 * - Press Escape or Backspace to clear the shortcut
 * - The shortcut is displayed in a user-friendly format
 */
class ShortcutTextField(initialValue: String = "") : JBTextField() {

    /**
     * The shortcut value in IntelliJ format (e.g., "meta R" for macOS, "ctrl R" for Windows/Linux)
     */
    var shortcutValue: String = initialValue
        private set

    init {
        isEditable = false
        text = convertToDisplayText(initialValue)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                e.consume()

                // Clear shortcut on Escape or Backspace (without modifiers)
                if ((e.keyCode == KeyEvent.VK_ESCAPE || e.keyCode == KeyEvent.VK_BACK_SPACE) && !hasModifiers(e)) {
                    shortcutValue = ""
                    text = ""
                    return
                }

                // Ignore standalone modifier keys
                if (isModifierKey(e.keyCode)) {
                    return
                }

                // Build the shortcut string
                val shortcut = buildShortcutString(e)
                if (shortcut.isNotEmpty()) {
                    shortcutValue = shortcut
                    text = convertToDisplayText(shortcut)
                }
            }

            override fun keyTyped(e: KeyEvent) {
                e.consume()
            }
        })
    }

    /**
     * Sets the shortcut value programmatically
     */
    fun setShortcut(value: String) {
        shortcutValue = value
        text = convertToDisplayText(value)
    }

    private fun hasModifiers(e: KeyEvent): Boolean {
        return e.isControlDown || e.isMetaDown || e.isAltDown || e.isShiftDown
    }

    private fun isModifierKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.VK_CONTROL ||
                keyCode == KeyEvent.VK_META ||
                keyCode == KeyEvent.VK_ALT ||
                keyCode == KeyEvent.VK_SHIFT ||
                keyCode == KeyEvent.VK_ALT_GRAPH
    }

    /**
     * Builds the shortcut string in IntelliJ format from a KeyEvent.
     * Format: "modifier1 modifier2 KEY" (e.g., "meta R", "ctrl shift A")
     */
    private fun buildShortcutString(e: KeyEvent): String {
        val parts = mutableListOf<String>()

        // Add modifiers in a consistent order
        if (e.isControlDown) parts.add("ctrl")
        if (e.isAltDown) parts.add("alt")
        if (e.isShiftDown) parts.add("shift")
        if (e.isMetaDown) parts.add("meta")

        // Get the key name
        val keyName = getKeyName(e.keyCode)
        if (keyName.isNotEmpty()) {
            parts.add(keyName)
        }

        return parts.joinToString(" ")
    }

    /**
     * Gets the key name from a key code in a format compatible with IntelliJ's CustomShortcutSet.fromString
     */
    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.VK_ENTER -> "ENTER"
            KeyEvent.VK_TAB -> "TAB"
            KeyEvent.VK_SPACE -> "SPACE"
            KeyEvent.VK_DELETE -> "DELETE"
            KeyEvent.VK_HOME -> "HOME"
            KeyEvent.VK_END -> "END"
            KeyEvent.VK_PAGE_UP -> "PAGE_UP"
            KeyEvent.VK_PAGE_DOWN -> "PAGE_DOWN"
            KeyEvent.VK_UP -> "UP"
            KeyEvent.VK_DOWN -> "DOWN"
            KeyEvent.VK_LEFT -> "LEFT"
            KeyEvent.VK_RIGHT -> "RIGHT"
            KeyEvent.VK_INSERT -> "INSERT"
            KeyEvent.VK_F1 -> "F1"
            KeyEvent.VK_F2 -> "F2"
            KeyEvent.VK_F3 -> "F3"
            KeyEvent.VK_F4 -> "F4"
            KeyEvent.VK_F5 -> "F5"
            KeyEvent.VK_F6 -> "F6"
            KeyEvent.VK_F7 -> "F7"
            KeyEvent.VK_F8 -> "F8"
            KeyEvent.VK_F9 -> "F9"
            KeyEvent.VK_F10 -> "F10"
            KeyEvent.VK_F11 -> "F11"
            KeyEvent.VK_F12 -> "F12"
            in KeyEvent.VK_A..KeyEvent.VK_Z -> KeyEvent.getKeyText(keyCode).uppercase()
            in KeyEvent.VK_0..KeyEvent.VK_9 -> KeyEvent.getKeyText(keyCode)
            else -> {
                val keyText = KeyEvent.getKeyText(keyCode)
                // Filter out unknown or modifier key texts
                if (keyText.startsWith("Unknown") || keyText.contains("Unknown")) {
                    ""
                } else {
                    keyText.uppercase().replace(" ", "_")
                }
            }
        }
    }

    /**
     * Converts the IntelliJ shortcut format to a user-friendly display text.
     * For example: "meta R" -> "⌘R" on macOS, "ctrl R" -> "Ctrl+R"
     */
    private fun convertToDisplayText(shortcut: String): String {
        if (shortcut.isBlank()) return ""

        val parts = shortcut.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""

        val displayParts = mutableListOf<String>()
        for (part in parts) {
            val displayPart = when (part.lowercase()) {
                "ctrl", "control" -> if (SystemInfo.isMac) "⌃" else "Ctrl"
                "alt" -> if (SystemInfo.isMac) "⌥" else "Alt"
                "shift" -> if (SystemInfo.isMac) "⇧" else "Shift"
                "meta" -> if (SystemInfo.isMac) "⌘" else "Win"
                else -> part.uppercase()
            }
            displayParts.add(displayPart)
        }

        return if (SystemInfo.isMac) {
            displayParts.joinToString("")
        } else {
            displayParts.joinToString("+")
        }
    }
}
