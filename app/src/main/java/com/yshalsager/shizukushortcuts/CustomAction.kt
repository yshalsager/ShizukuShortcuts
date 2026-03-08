package com.yshalsager.shizukushortcuts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomAction(
    val id: String,
    val label: String,
    val shell_command: String
)

interface CustomActionsRepositoryContract {
    val actions: StateFlow<List<CustomAction>>
    fun add_action(label: String, shell_command: String): CustomAction
    fun update_action(action_id: String, label: String, shell_command: String)
    fun delete_action(action_id: String)
    fun find_by_id(action_id: String): CustomAction?
}

class AppCustomActionsRepository(app_context: Context) : CustomActionsRepositoryContract {
    companion object {
        private const val prefs_name = "custom_actions"
        private const val actions_key = "actions"
    }

    private val app_context = app_context.applicationContext
    private val shared_preferences = this.app_context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
    private val state_flow = MutableStateFlow(parse_custom_actions(shared_preferences.getString(actions_key, null)))
    private val shortcut_sync_scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override val actions: StateFlow<List<CustomAction>> = state_flow.asStateFlow()

    init {
        schedule_shortcut_sync(state_flow.value)
    }

    override fun add_action(label: String, shell_command: String): CustomAction {
        val action = CustomAction(
            id = UUID.randomUUID().toString(),
            label = label.trim(),
            shell_command = shell_command.trim()
        )
        save_actions(state_flow.value + action)
        return action
    }

    override fun update_action(action_id: String, label: String, shell_command: String) {
        save_actions(
            state_flow.value.map { action ->
                if (action.id != action_id) action
                else action.copy(label = label.trim(), shell_command = shell_command.trim())
            }
        )
    }

    override fun delete_action(action_id: String) {
        save_actions(
            actions = state_flow.value.filterNot { it.id == action_id },
            deleted_action_id = action_id
        )
    }

    override fun find_by_id(action_id: String) = state_flow.value.firstOrNull { it.id == action_id }

    private fun save_actions(actions: List<CustomAction>, deleted_action_id: String? = null) {
        shared_preferences.edit().putString(actions_key, serialize_custom_actions(actions)).apply()
        state_flow.value = actions
        schedule_shortcut_sync(actions, deleted_action_id)
    }

    private fun schedule_shortcut_sync(actions: List<CustomAction>, deleted_action_id: String? = null) {
        shortcut_sync_scope.launch {
            deleted_action_id?.let { DynamicShortcutSync.delete_custom_shortcut(app_context, it) }
            DynamicShortcutSync.refresh_custom_shortcuts(app_context, actions)
        }
    }
}

fun validate_custom_action(label: String, shell_command: String): Int? {
    val trimmed_label = label.trim()
    val trimmed_command = shell_command.trim()

    return when {
        trimmed_label.isEmpty() -> R.string.custom_action_label_required
        trimmed_command.isEmpty() -> R.string.custom_action_command_required
        trimmed_command.startsWith("adb shell", ignoreCase = true) -> R.string.custom_action_strip_adb
        else -> null
    }
}

fun serialize_custom_actions(actions: List<CustomAction>): String {
    val json_array = JSONArray()

    actions.forEach { action ->
        json_array.put(
            JSONObject()
                .put("id", action.id)
                .put("label", action.label)
                .put("shell_command", action.shell_command)
        )
    }

    return json_array.toString()
}

fun parse_custom_actions(serialized_actions: String?): List<CustomAction> {
    if (serialized_actions.isNullOrBlank()) return emptyList()

    val json_array = JSONArray(serialized_actions)

    return List(json_array.length()) { index ->
        val json_object = json_array.getJSONObject(index)
        CustomAction(
            id = json_object.getString("id"),
            label = json_object.getString("label"),
            shell_command = json_object.getString("shell_command")
        )
    }
}
