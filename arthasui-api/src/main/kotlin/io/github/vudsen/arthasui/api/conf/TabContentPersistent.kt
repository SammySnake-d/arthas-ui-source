package io.github.vudsen.arthasui.api.conf

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * State class for storing tab content
 */
class TabContentState(
    /**
     * Map of tabId to content. The key is the unique tab identifier,
     * and the value is the command content saved in that tab.
     */
    var tabContents: MutableMap<String, String> = mutableMapOf()
)

/**
 * Persistent service for saving and loading tab content (commands).
 * This allows commands in execute tabs to be preserved across IDE restarts.
 */
@State(
    name = "io.github.vudsen.arthasui.api.conf.TabContentPersistent",
    storages = [Storage("ArthasUITabContent.xml")]
)
@Service(Service.Level.APP)
class TabContentPersistent : PersistentStateComponent<TabContentState> {

    private var myState = TabContentState()

    override fun getState(): TabContentState {
        return myState
    }

    override fun loadState(state: TabContentState) {
        this.myState = state
    }

    /**
     * Get content for a specific tab
     * @param tabId The unique identifier for the tab
     * @return The saved content, or empty string if not found
     */
    fun getContent(tabId: String): String {
        return myState.tabContents[tabId] ?: ""
    }

    /**
     * Save content for a specific tab
     * @param tabId The unique identifier for the tab
     * @param content The content to save
     */
    fun setContent(tabId: String, content: String) {
        myState.tabContents[tabId] = content
    }

    /**
     * Remove content for a specific tab
     * @param tabId The unique identifier for the tab
     */
    fun removeContent(tabId: String) {
        myState.tabContents.remove(tabId)
    }

    /**
     * Check if content exists for a specific tab
     * @param tabId The unique identifier for the tab
     * @return true if content exists, false otherwise
     */
    fun hasContent(tabId: String): Boolean {
        return myState.tabContents.containsKey(tabId)
    }
}
