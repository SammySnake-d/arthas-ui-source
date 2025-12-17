package io.github.vudsen.arthasui.api.conf

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing a persisted tab.
 */
data class PersistedTab(
    var id: String = "",
    var name: String = ""
)

/**
 * State class for storing tabs per JVM.
 * Key format: "hostMachineId:providerId:jvmId" to uniquely identify a JVM context.
 */
class TabPersistentState(
    /**
     * Map of jvmKey to list of tabs.
     */
    var jvmTabs: MutableMap<String, MutableList<PersistedTab>> = ConcurrentHashMap()
)

/**
 * Persistent service for saving and loading tabs per JVM.
 * This allows tabs to be preserved across IDE restarts and refresh operations.
 */
@State(
    name = "io.github.vudsen.arthasui.api.conf.TabPersistent",
    storages = [Storage("ArthasUITabs.xml")]
)
@Service(Service.Level.APP)
class TabPersistent : PersistentStateComponent<TabPersistentState> {

    private var myState = TabPersistentState()

    override fun getState(): TabPersistentState {
        return myState
    }

    override fun loadState(state: TabPersistentState) {
        this.myState = state
    }

    /**
     * Generate a unique key for a JVM context.
     * @param hostMachineId The host machine ID
     * @param providerId The provider type
     * @param jvmId The JVM ID
     * @return A unique key string
     */
    fun generateJvmKey(hostMachineId: Long, providerId: String, jvmId: String): String {
        return "$hostMachineId:$providerId:$jvmId"
    }

    /**
     * Get tabs for a specific JVM.
     * @param jvmKey The unique JVM key
     * @return List of persisted tabs, or empty list if none found
     */
    fun getTabs(jvmKey: String): List<PersistedTab> {
        return myState.jvmTabs[jvmKey]?.toList() ?: emptyList()
    }

    /**
     * Set tabs for a specific JVM.
     * @param jvmKey The unique JVM key
     * @param tabs The list of tabs to save
     */
    fun setTabs(jvmKey: String, tabs: List<PersistedTab>) {
        if (tabs.isEmpty()) {
            myState.jvmTabs.remove(jvmKey)
        } else {
            myState.jvmTabs[jvmKey] = tabs.toMutableList()
        }
    }

    /**
     * Add a tab to a specific JVM.
     * @param jvmKey The unique JVM key
     * @param tab The tab to add
     */
    fun addTab(jvmKey: String, tab: PersistedTab) {
        val tabs = myState.jvmTabs.getOrPut(jvmKey) { mutableListOf() }
        tabs.add(tab)
    }

    /**
     * Remove a tab from a specific JVM.
     * @param jvmKey The unique JVM key
     * @param tabId The tab ID to remove
     */
    fun removeTab(jvmKey: String, tabId: String) {
        myState.jvmTabs[jvmKey]?.removeIf { it.id == tabId }
        // Clean up empty lists
        if (myState.jvmTabs[jvmKey]?.isEmpty() == true) {
            myState.jvmTabs.remove(jvmKey)
        }
    }

    /**
     * Update a tab name.
     * @param jvmKey The unique JVM key
     * @param tabId The tab ID to update
     * @param newName The new name for the tab
     * @return true if the tab was found and updated, false otherwise
     */
    fun updateTabName(jvmKey: String, tabId: String, newName: String): Boolean {
        val tabs = myState.jvmTabs[jvmKey] ?: return false
        val tab = tabs.find { it.id == tabId } ?: return false
        tab.name = newName
        return true
    }
}
